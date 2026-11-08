package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.dto.PaymentConfirmResponse;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.payment.service.PaymentTxService.AlreadyProcessed;
import com.team7.rental.payment.service.PaymentTxService.AmountMismatch;
import com.team7.rental.payment.service.PaymentTxService.ConfirmDecision;
import com.team7.rental.payment.service.PaymentTxService.OrderResult;
import com.team7.rental.payment.service.PaymentTxService.Proceed;
import com.team7.rental.rental.dto.RentalExtensionConfirmed;
import com.team7.rental.rental.dto.RentalExtensionPaymentView;
import com.team7.rental.rental.dto.RentalPaymentView;
import com.team7.rental.rental.repository.ExtensionRef;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/**
 * 연장료 결제의 트랜잭션 부분 (REQ-038). 흐름은 대여 결제({@link PaymentTxService})와 같고, 연장의 유효성은 연장 행(PENDING_PAYMENT, expires_at)으로 본다.
 * <p>
 * <b>락 순서: 대여 행 → 연장 행 → 결제 행.</b> 주문 생성·승인 TX1·TX2·만료 배치·조정 배치가 모두 이 순서로 잡는다(notes.md "락 순서").
 * 대여 결제 경로(대여 → 결제)와 환불 경로(대여 → 결제)는 이 순서의 부분 순서라 서로 교착하지 않는다.
 * <p>
 * 연장은 RENTING일 때만 확정한다. 승인 시점에 만료됐거나 대여가 RENTING이 아니거나 종료일이 바뀌었으면(연체 배치·반납 요청과의 경합)
 * 연장을 CANCELED로 두고 연장료를 전액 환불한다(open-issues 4-A). 새 상태 전이를 만들지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ExtensionPaymentTxService {

	/**
	 * 승인 반영 결과.
	 *
	 * @param refund 확정할 수 없어 이어서 할 전액 환불. 다른 요청이 이미 맡았으면 null
	 * @param canceledAfterExpiry PAYMENT_CANCELED_AFTER_EXPIRY로 답한다
	 * @param extended 이번에 확정했으면 커밋 뒤 보낼 알림 대상
	 */
	record ExtensionConfirmOutcome(PaymentConfirmResponse response, RefundTicket refund, boolean canceledAfterExpiry,
		RentalExtensionConfirmed extended) {
	}

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final RentalPaymentTxService rentalPaymentTxService;
	private final PaymentRefundTxService paymentRefundTxService;
	private final Clock clock;

	/**
	 * 연장료 주문. 진행 중인(READY) 주문이 연장에 묶여 있으면 그대로 돌려주고, 실패한 뒤에는 새 주문을 만들어 연장에 다시 묶는다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, PAYMENT_RENTER_ONLY, RENTAL_EXTENSION_NOT_FOUND,
	 *     RENTAL_INVALID_TRANSITION(결제 대기가 아님), PAYMENT_EXPIRED
	 */
	public OrderResult createOrder(Long rentalId, Long extensionId, Long memberId) {
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		if (!rental.renterId().equals(memberId)) {
			throw new BusinessException(ErrorCode.PAYMENT_RENTER_ONLY);
		}
		RentalExtensionPaymentView extension = rentalPaymentTxService.lockExtension(rentalId, extensionId);
		if (!extension.isPendingPayment()) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		if (!extension.pending()) {
			throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
		}
		if (extension.paymentId() != null) {
			Payment current = paymentRepository.findByIdForUpdate(extension.paymentId()).orElseThrow();
			if (current.getStatus() == PaymentStatus.READY) {
				return new OrderResult(current.getOrderId(), current.getAmount(), rental.itemId());
			}
		}
		Payment payment = paymentRepository.save(Payment.createForExtension(rentalId, UUID.randomUUID().toString(),
			UUID.randomUUID().toString(), extension.additionalFee()));
		rentalPaymentTxService.attachExtensionOrder(extensionId, payment.getId());
		return new OrderResult(payment.getOrderId(), payment.getAmount(), rental.itemId());
	}

	/**
	 * TX1: 검증과 시도 기록. {@link PaymentTxService#prepareConfirm}과 같은 규칙이고, 유효성은 연장으로 본다.
	 * 금액 불일치는 확정 실패라 결제 FAILED와 함께 연장도 바로 CANCELED다.
	 *
	 * @throws BusinessException PAYMENT_RENTER_ONLY, PAYMENT_INVALID_STATUS(FAILED 주문. 실패 뒤 새 주문이 연장에 묶이면 옛 주문은 연장에서 떨어진다),
	 *     PAYMENT_EXPIRED, PAYMENT_CANCELED_AFTER_EXPIRY
	 */
	public ConfirmDecision prepareConfirm(Long rentalId, Long paymentId, Long memberId, String paymentKey,
		int requestAmount) {
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		if (!rental.renterId().equals(memberId)) {
			throw new BusinessException(ErrorCode.PAYMENT_RENTER_ONLY);
		}
		RentalExtensionPaymentView extension = lockExtensionOf(rentalId, paymentId);
		Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		if (isCanceledAfterExpiry(payment, extension)) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCELED_AFTER_EXPIRY);
		}
		if (payment.getStatus() != PaymentStatus.READY) {
			return new AlreadyProcessed(PaymentConfirmResponse.of(payment));
		}
		if (!extension.pending()) {
			throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
		}
		if (payment.getAmount() != requestAmount) {
			payment.fail();
			rentalPaymentTxService.cancelExtension(extension.extensionId());
			return new AmountMismatch();
		}
		payment.recordPaymentKey(paymentKey);
		PaymentTransaction transaction = paymentTransactionRepository.save(PaymentTransaction.pending(payment.getId(),
			PaymentTransactionAction.APPROVE, payment.getAmount(), payment.getIdempotencyKey()));
		return new Proceed(rentalId, transaction.getId(), payment.getIdempotencyKey(), payment.getAmount());
	}

	/** TX2: 승인 성공 반영. 연장이 아직 유효하고 대여가 RENTING이면 확정, 아니면 연장 CANCELED + 전액 환불 준비 */
	public ExtensionConfirmOutcome applyApproved(Long rentalId, Long paymentId, Long transactionId,
		PgResult.Success result) {
		rentalPaymentTxService.lock(rentalId);
		RentalExtensionPaymentView extension = lockExtensionOf(rentalId, paymentId);
		Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
		paymentTransactionRepository.findById(transactionId).orElseThrow().succeed(result.toRecord());
		if (payment.getStatus() != PaymentStatus.READY && payment.getStatus() != PaymentStatus.FAILED) {
			return new ExtensionConfirmOutcome(PaymentConfirmResponse.of(payment), null,
				isCanceledAfterExpiry(payment, extension), null);
		}
		return approveAndSettle(rentalId, extension, payment, result.payment().paymentKey());
	}

	/**
	 * 승인 결과 반영 공통(TX2·조정 배치). 호출하는 쪽이 대여 → 연장 → 결제 행 락을 잡았다.
	 */
	ExtensionConfirmOutcome approveAndSettle(Long rentalId, RentalExtensionPaymentView extension, Payment payment,
		String paymentKey) {
		LocalDateTime now = LocalDateTime.now(clock);
		payment.approve(paymentKey);
		if (extension.pending() && extension.applicable()) {
			RentalExtensionConfirmed confirmed = rentalPaymentTxService.confirmExtension(rentalId,
				extension.extensionId(), payment.getId(), now);
			return new ExtensionConfirmOutcome(PaymentConfirmResponse.of(payment), null, false, confirmed);
		}
		if (extension.isPendingPayment()) {
			rentalPaymentTxService.cancelExtension(extension.extensionId());
		}
		RefundTicket refund = paymentRefundTxService.prepare(payment, payment.getAmount(),
			RefundPurpose.EXTENSION_CANCEL, null);
		return new ExtensionConfirmOutcome(PaymentConfirmResponse.of(payment), refund, true, null);
	}

	/**
	 * 연장에서 떨어진 옛 주문이 PG에서 승인된 것을 조정 배치가 찾았을 때: 승인으로 맞추고 전액 환불을 준비한다(키 extension-refund:{paymentId}).
	 * 호출하는 쪽이 대여 행 → 결제 행 락을 잡았다. 연장 상태는 건드리지 않는다(현재 주문이 따로 있다).
	 */
	RefundTicket refundDetached(Payment payment, String paymentKey) {
		payment.approve(paymentKey);
		return paymentRefundTxService.prepare(payment, payment.getAmount(), RefundPurpose.EXTENSION_CANCEL, null);
	}

	/** 연장 결제 만료 배치 대상 */
	@Transactional(readOnly = true)
	public List<ExtensionRef> findExpired(LocalDateTime now) {
		return rentalPaymentTxService.findExpiredExtensions(now);
	}

	/**
	 * 연장 결제 만료 한 건: PENDING_PAYMENT → CANCELED, 묶인 READY 주문은 FAILED(승인 결과를 모르는 주문은 조정 배치 몫).
	 * 대상 조회 뒤 승인됐을 수 있어 행 락을 잡고 다시 본다.
	 *
	 * @return 처리했으면 true
	 */
	public boolean expire(ExtensionRef ref, LocalDateTime now) {
		rentalPaymentTxService.lock(ref.rentalId());
		RentalExtensionPaymentView extension = rentalPaymentTxService.lockExtension(ref.rentalId(),
			ref.extensionId());
		if (!extension.isPendingPayment() || extension.pending()) {
			return false;
		}
		rentalPaymentTxService.cancelExtension(extension.extensionId());
		if (extension.paymentId() != null) {
			paymentRefundTxService.closeReadyOrder(extension.paymentId());
		}
		return true;
	}

	/**
	 * 조정 배치가 대여 행 락 다음, 결제 행 락 전에 부른다. 실패 뒤 새 주문에 밀려 연장에서 떨어진 옛 주문이면 비어 있다.
	 */
	Optional<RentalExtensionPaymentView> lockExtensionForReconcile(Long rentalId, Long paymentId) {
		return rentalPaymentTxService.lockExtensionByPayment(rentalId, paymentId);
	}

	// 연장에 묶이지 않은 주문은 실패한 뒤 새 주문에 밀린 옛 주문뿐이다(FAILED)
	private RentalExtensionPaymentView lockExtensionOf(Long rentalId, Long paymentId) {
		return rentalPaymentTxService.lockExtensionByPayment(rentalId, paymentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS));
	}

	// 승인은 됐는데 연장이 확정되지 못했다: 경합으로 환불 중이거나 환불이 남은 건이다
	private boolean isCanceledAfterExpiry(Payment payment, RentalExtensionPaymentView extension) {
		return payment.getStatus() == PaymentStatus.APPROVED && !extension.isConfirmed();
	}
}
