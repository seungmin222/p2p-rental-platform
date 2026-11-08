package com.team7.rental.rental.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

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
 * 대여 연장 요청 (REQ-038). 연장 기간은 [prevEndDate + 1, newEndDate]다. 결제 대기(PENDING_PAYMENT)는 expiresAt 전까지 그 기간을 점유한다.
 * 만료 배치가 늦어도 판단이 바뀌지 않게 상태만이 아니라 시각까지 본다(rental_cancel_agreements와 같다).
 * <p>
 * {@code @Version}을 두지 않는다. 모든 쓰기는 대여 행 락 → 연장 행 락 순서로 직렬화된다(notes.md "락 순서").
 */
@Getter
@Entity
@Table(name = "rental_extensions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalExtension extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Column(nullable = false)
	private LocalDate prevEndDate;

	@Column(nullable = false)
	private LocalDate newEndDate;

	@Column(nullable = false)
	private int additionalFee;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ExtensionStatus status;

	/** 결제 대기 만료. 바로 확정한 연장은 요청 시각 */
	@Column(nullable = false)
	private LocalDateTime expiresAt;

	/** 승인된 EXTENSION 결제. 결제 없이 확정한 연장(0원·결제 기능 꺼짐)은 null */
	private Long paymentId;

	/**
	 * 결제 대기로 생성. 연장료·기간 검증은 호출하는 서비스가 한다.
	 *
	 * @param expiresAt 서비스가 Clock + payment.expire-minutes로 계산
	 */
	public static RentalExtension pendingPayment(Long rentalId, LocalDate prevEndDate, LocalDate newEndDate,
		int additionalFee, LocalDateTime expiresAt) {
		RentalExtension extension = create(rentalId, prevEndDate, newEndDate, additionalFee,
			ExtensionStatus.PENDING_PAYMENT);
		extension.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
		return extension;
	}

	/** 결제 없이 바로 확정(연장료 0원 또는 결제 기능 꺼짐) */
	public static RentalExtension confirmedWithoutPayment(Long rentalId, LocalDate prevEndDate,
		LocalDate newEndDate, int additionalFee, LocalDateTime now) {
		RentalExtension extension = create(rentalId, prevEndDate, newEndDate, additionalFee,
			ExtensionStatus.CONFIRMED);
		extension.expiresAt = Objects.requireNonNull(now, "now");
		return extension;
	}

	private static RentalExtension create(Long rentalId, LocalDate prevEndDate, LocalDate newEndDate,
		int additionalFee, ExtensionStatus status) {
		if (prevEndDate == null || newEndDate == null || !newEndDate.isAfter(prevEndDate)) {
			throw new IllegalArgumentException("연장 기간이 올바르지 않습니다: " + prevEndDate + " → " + newEndDate);
		}
		if (additionalFee < 0) {
			throw new IllegalArgumentException("연장료는 0원 이상이어야 합니다: " + additionalFee);
		}
		RentalExtension extension = new RentalExtension();
		extension.rentalId = rentalId;
		extension.prevEndDate = prevEndDate;
		extension.newEndDate = newEndDate;
		extension.additionalFee = additionalFee;
		extension.status = status;
		return extension;
	}

	/** 연장 기간의 첫날 (기존 종료일 다음 날) */
	public LocalDate firstDate() {
		return prevEndDate.plusDays(1);
	}

	/** 연장 일수 */
	public int days() {
		return Rental.countDays(firstDate(), newEndDate);
	}

	/** 결제 대기이고 만료 전. 정확히 만료 시각이면 만료로 본다(대여 결제 대기와 같은 기준) */
	public boolean isPendingAt(LocalDateTime now) {
		return status == ExtensionStatus.PENDING_PAYMENT && expiresAt.isAfter(now);
	}

	public boolean isExpiredAt(LocalDateTime now) {
		return status == ExtensionStatus.PENDING_PAYMENT && !expiresAt.isAfter(now);
	}

	/**
	 * 현재 주문을 묶는다(주문 생성 때, 실패 뒤 새 주문이면 바꾼다). 승인 때 주문으로 연장을 찾는 기준이다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void attachOrder(Long paymentId) {
		requirePending();
		this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
	}

	/**
	 * PENDING_PAYMENT → CONFIRMED (결제 승인). 만료 여부는 호출하는 쪽이 먼저 본다(만료 뒤 승인은 확정하지 않고 환불한다).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void confirm(Long paymentId) {
		requirePending();
		this.status = ExtensionStatus.CONFIRMED;
		this.paymentId = paymentId;
	}

	/**
	 * PENDING_PAYMENT → CANCELED (만료·금액 불일치·승인 시점에 대여가 RENTING이 아님).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void cancel() {
		requirePending();
		this.status = ExtensionStatus.CANCELED;
	}

	private void requirePending() {
		if (status != ExtensionStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}
}
