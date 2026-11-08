package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.ExtensionStatus;

/**
 * 결제 도메인이 보는 연장 (REQ-038). 대여 행 락 → 연장 행 락을 잡은 뒤 만든다.
 *
 * @param paymentId 연장에 묶인 현재 주문(실패 뒤 새 주문이면 바뀐다). 아직 주문이 없으면 null
 * @param pending 결제 대기이고 만료 전
 * @param applicable 지금 확정할 수 있음: 대여가 RENTING이고 종료일이 요청 때와 같다
 */
public record RentalExtensionPaymentView(Long extensionId, Long rentalId, ExtensionStatus status, int additionalFee,
	Long paymentId, boolean pending, boolean applicable) {

	public boolean isConfirmed() {
		return status == ExtensionStatus.CONFIRMED;
	}

	public boolean isPendingPayment() {
		return status == ExtensionStatus.PENDING_PAYMENT;
	}
}
