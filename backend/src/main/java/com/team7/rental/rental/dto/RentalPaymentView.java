package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.RentalStatus;

/**
 * 결제 도메인이 보는 대여 정보 (엔티티를 넘기지 않는다).
 *
 * @param paymentExpired 결제 대기이고 만료 시각이 지났는지
 */
public record RentalPaymentView(Long rentalId, Long itemId, Long renterId, RentalStatus status, int rentalFee,
	int deposit, int overdueFee, int cancelFee, boolean paymentExpired) {

	public boolean isPayable() {
		return status == RentalStatus.PENDING_PAYMENT && !paymentExpired;
	}

	/** 결제 승인으로 RESERVED 이상이 됐는지 */
	public boolean isReservedOrLater() {
		return status != RentalStatus.PENDING_PAYMENT && status != RentalStatus.CANCELED;
	}
}
