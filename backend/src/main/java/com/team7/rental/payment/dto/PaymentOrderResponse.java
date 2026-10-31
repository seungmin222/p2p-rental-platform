package com.team7.rental.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentOrderResponse(
	@Schema(description = "토스 결제창에 넘길 주문번호", example = "3f2b8a2e-7c1d-4a55-9f0a-0d6c1c1e2b11")
	String orderId,

	@Schema(description = "결제 금액(대여료 + 보증금, 서버 계산)", example = "35000")
	int amount,

	@Schema(description = "주문명(물품 이름)", example = "캠핑 텐트")
	String orderName,

	@Schema(description = "토스 결제창에 넘길 구매자 키", example = "9a1b3c1e-6f0a-3d2e-8c4b-1a2b3c4d5e6f")
	String customerKey) {
}
