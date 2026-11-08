package com.team7.rental.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 주문 생성 (REQ-024, REQ-038). 금액은 서버가 계산한다. extensionId가 있으면 그 연장의 연장료 주문이다 */
public record PaymentCreateRequest(
	@Schema(description = "대여 ID", example = "1") @NotNull
	Long rentalId,

	@Schema(description = "연장 ID(REQ-038). 연장 요청 응답의 extensionId. 없으면 대여료+보증금 주문", example = "3")
	Long extensionId) {
}
