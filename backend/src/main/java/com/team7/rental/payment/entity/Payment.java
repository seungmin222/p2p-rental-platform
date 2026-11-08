package com.team7.rental.payment.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 (REQ-024·025). 대여는 ID로만 참조한다. 금액 0원이면 행을 만들지 않는다(docs/domains/payment.md 원칙 7).
 * 전이 메서드는 상태 가드만 보고, 위반 시 PAYMENT_INVALID_STATUS(409)다. 동시 변경은 서비스가 행 락으로 막는다.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentType type;

	@Column(nullable = false, length = 64)
	private String orderId;

	/** 승인 요청 때 받는다. 그 전에는 null */
	@Column(length = 200)
	private String paymentKey;

	@Column(nullable = false)
	private int amount;

	@Column(nullable = false)
	private int rentalFeeAmount;

	@Column(nullable = false)
	private int depositAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentStatus status;

	/** type=RENTAL이고 승인된 뒤에만 값이 있다 */
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private DepositStatus depositStatus;

	/** 보증금 보류 시작 (REQ-049). deposit.hold-agreement-days 관리자 확정 기준 */
	private LocalDateTime heldAt;

	@Column(nullable = false)
	private int refundedAmount;

	/** 아직 끝나지 않은 환불액. null이 아니면 환불이 남아 있다 */
	private Integer refundPendingAmount;

	/** 마지막 PG 취소 시도 시각. 재시도 간격의 기준 */
	private LocalDateTime lastRefundAttemptAt;

	@Column(nullable = false)
	private int refundRetryCount;

	@Column(nullable = false)
	private boolean needsAdminCheck;

	/** 확정된 손상 등급 (REQ-049). 확정 때 한 번만 쓴다. 협의 이력은 deduction_proposals */
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private DamageGrade damageGrade;

	/** 확정된 손상 차감액 */
	private Integer deductionAmount;

	/** 확정된 차감 사유 */
	@Column(length = 500)
	private String deductionReason;

	/** 승인 API 멱등성 키 */
	@Column(nullable = false, length = 100)
	private String idempotencyKey;

	/**
	 * 대여료 + 보증금 주문(READY).
	 *
	 * @throws IllegalArgumentException 결제 금액이 0원 이하일 때. 0원은 결제 없이 진행한다
	 */
	public static Payment createForRental(Long rentalId, String orderId, String idempotencyKey, int rentalFee,
		int deposit) {
		int amount = Math.addExact(rentalFee, deposit);
		if (amount <= 0) {
			throw new IllegalArgumentException("결제 금액은 0원보다 커야 합니다: " + amount);
		}
		Payment payment = new Payment();
		payment.rentalId = rentalId;
		payment.type = PaymentType.RENTAL;
		payment.orderId = orderId;
		payment.idempotencyKey = idempotencyKey;
		payment.amount = amount;
		payment.rentalFeeAmount = rentalFee;
		payment.depositAmount = deposit;
		payment.status = PaymentStatus.READY;
		return payment;
	}

	/**
	 * 연장료 주문(READY, REQ-038). 대여료·보증금 칸은 0이고 보증금 상태는 끝까지 null이다(open-issues #1).
	 *
	 * @throws IllegalArgumentException 연장료가 0원 이하일 때. 0원 연장은 결제 없이 확정한다
	 */
	public static Payment createForExtension(Long rentalId, String orderId, String idempotencyKey,
		int extensionFee) {
		if (extensionFee <= 0) {
			throw new IllegalArgumentException("연장료는 0원보다 커야 합니다: " + extensionFee);
		}
		Payment payment = new Payment();
		payment.rentalId = rentalId;
		payment.type = PaymentType.EXTENSION;
		payment.orderId = orderId;
		payment.idempotencyKey = idempotencyKey;
		payment.amount = extensionFee;
		payment.status = PaymentStatus.READY;
		return payment;
	}

	/** 승인 요청에 받은 paymentKey를 기록한다(TX1). 결과를 모를 때 조정 배치가 취소에 쓴다 */
	public void recordPaymentKey(String paymentKey) {
		this.paymentKey = paymentKey;
	}

	/**
	 * READY·FAILED → APPROVED, 대여 결제면 보증금 FROZEN(0원이면 NONE). 연장 결제는 보증금 상태를 두지 않는다. FAILED는 조정 배치가 PG에서 승인을 확인했을 때만 온다.
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS
	 */
	public void approve(String paymentKey) {
		if (status != PaymentStatus.READY && status != PaymentStatus.FAILED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.paymentKey = paymentKey;
		this.status = PaymentStatus.APPROVED;
		if (type == PaymentType.RENTAL) {
			this.depositStatus = depositAmount > 0 ? DepositStatus.FROZEN : DepositStatus.NONE;
		}
	}

	/**
	 * READY → FAILED (승인 실패·금액 불일치·미결제 만료).
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS
	 */
	public void fail() {
		if (status != PaymentStatus.READY) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.status = PaymentStatus.FAILED;
	}

	/**
	 * 환불을 시작한다(TX1). 결과는 {@link #applyRefund} 또는 {@link #markRefundFailed}.
	 *
	 * @throws IllegalArgumentException 금액이 0원 이하이거나 남은 금액보다 클 때. 0원은 PG를 부르지 않는다
	 * @throws BusinessException PAYMENT_INVALID_STATUS: 승인된 결제가 아니거나 진행 중인 환불이 있을 때
	 */
	public void requestRefund(int refundAmount, LocalDateTime now) {
		if (status != PaymentStatus.APPROVED || refundPendingAmount != null) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		if (refundAmount <= 0 || refundAmount > balance()) {
			throw new IllegalArgumentException("환불 금액이 올바르지 않습니다: " + refundAmount);
		}
		this.refundPendingAmount = refundAmount;
		this.lastRefundAttemptAt = now;
	}

	/** 재시도 TX1: PG를 다시 부르기 직전 시각을 남긴다 */
	public void recordRefundAttempt(LocalDateTime now) {
		this.lastRefundAttemptAt = now;
	}

	/**
	 * 환불 성공(TX2). 누적 취소가 결제 금액에 닿으면 CANCELED, 아니면 PARTIAL_CANCELED. 보증금이 있으면 REFUNDED(0원이면 NONE 유지).
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS: 진행 중인 환불이 없을 때
	 */
	public void applyRefund() {
		if (refundPendingAmount == null) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.refundedAmount = Math.addExact(refundedAmount, refundPendingAmount);
		this.refundPendingAmount = null;
		this.status = refundedAmount == amount ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
		if (hasDepositStatus()) {
			this.depositStatus = DepositStatus.REFUNDED;
		}
	}

	/** 환불 실패·결과 불명(TX2). 환불액은 남아 재시도 배치 대상이 된다. 보증금 0원이면 NONE 유지(재시도는 refundPendingAmount로 찾는다) */
	public void markRefundFailed() {
		if (hasDepositStatus()) {
			this.depositStatus = DepositStatus.REFUND_FAILED;
		}
	}

	/** 재시도 실패를 센다. maxRetry회에 닿으면 관리자 확인 대상이 되고 재시도 대상에서 빠진다 */
	public void countRefundRetryFailure(int maxRetry) {
		this.refundRetryCount++;
		if (refundRetryCount >= maxRetry) {
			this.needsAdminCheck = true;
		}
	}

	/**
	 * 상태 확인 '이상'·미반납 신고: FROZEN → HELD (REQ-049).
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS
	 */
	public void holdDeposit(LocalDateTime now) {
		if (depositStatus != DepositStatus.FROZEN) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.depositStatus = DepositStatus.HELD;
		this.heldAt = now;
	}

	/**
	 * 차감 확정 결과를 남긴다 (REQ-049). 보류 중이고 진행 중인 환불이 없을 때 한 번만. 금액 검증은 호출하는 쪽이 한다.
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS
	 */
	public void recordDeduction(DamageGrade grade, int amount, String reason) {
		if (!isDepositSettleable() || damageGrade != null) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.damageGrade = grade;
		this.deductionAmount = amount;
		this.deductionReason = reason;
	}

	/**
	 * 반환액 0원: PG 호출 없이 FROZEN(REQ-025)·HELD(차감 확정, REQ-049) → FORFEITED.
	 *
	 * @throws BusinessException PAYMENT_INVALID_STATUS
	 */
	public void forfeitDeposit() {
		if (depositStatus != DepositStatus.FROZEN && depositStatus != DepositStatus.HELD) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		this.depositStatus = DepositStatus.FORFEITED;
	}

	/** 조정 배치가 고칠 수 없는 불일치를 찾았을 때 */
	public void flagForAdminCheck() {
		this.needsAdminCheck = true;
	}

	// 보증금 상태를 바꿀 대상: 승인된 대여 결제이고 보증금이 있다. 연장 결제(null)·보증금 0원(NONE)은 그대로 둔다
	private boolean hasDepositStatus() {
		return depositStatus != null && depositStatus != DepositStatus.NONE;
	}

	/** 아직 취소되지 않은 금액 */
	public int balance() {
		return amount - refundedAmount;
	}

	public boolean isDepositFrozen() {
		return depositStatus == DepositStatus.FROZEN;
	}

	/** 차감 협의·확정을 할 수 있는지: 보류 중이고 진행 중인 환불이 없다 */
	public boolean isDepositSettleable() {
		return depositStatus == DepositStatus.HELD && refundPendingAmount == null;
	}
}
