package com.team7.rental.payment.dto;

import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentConfirmResponse(
	@Schema(description = "주문번호", example = "3f2b8a2e-7c1d-4a55-9f0a-0d6c1c1e2b11")
	String orderId,

	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "결제 금액", example = "35000")
	int amount,

	@Schema(description = "결제 상태")
	PaymentStatus status,

	@Schema(description = "보증금 상태")
	DepositStatus depositStatus) {

	public static PaymentConfirmResponse of(Payment payment) {
		return new PaymentConfirmResponse(payment.getOrderId(), payment.getRentalId(), payment.getAmount(),
			payment.getStatus(), payment.getDepositStatus());
	}
}
