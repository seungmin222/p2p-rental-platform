package com.team7.rental.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 결제 승인 (REQ-024). 토스 성공 리다이렉트로 받은 값을 그대로 보낸다. amount는 저장된 금액과 비교하는 검증용이다 */
public record PaymentConfirmRequest(
	@Schema(description = "토스 paymentKey", example = "tviva20240101000000abcde") @NotBlank @Size(max = 200)
	String paymentKey,

	@Schema(description = "주문번호", example = "3f2b8a2e-7c1d-4a55-9f0a-0d6c1c1e2b11") @NotBlank @Size(max = 64)
	String orderId,

	@Schema(description = "결제 금액", example = "35000") @NotNull
	Integer amount) {
}
