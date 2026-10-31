package com.team7.rental.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 주문 생성 (REQ-024). 금액은 서버가 계산한다. 연장 결제의 extensionId는 REQ-038에서 추가한다 */
public record PaymentCreateRequest(
	@Schema(description = "대여 ID", example = "1") @NotNull
	Long rentalId) {
}
