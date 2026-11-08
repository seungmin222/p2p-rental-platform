package com.team7.rental.payment.dto;

import com.team7.rental.global.response.PageResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 정산 내역 (REQ-039). 합계는 페이지와 상관없이 본인 정산 전체 기준이다 */
public record SettlementListResponse(
	@Schema(description = "지급 예정(SCHEDULED) 지급액 합계(원). 보류(HELD)는 포함하지 않는다", example = "27000")
	long scheduledTotal,

	@Schema(description = "지급 완료(PAID) 지급액 합계(원)", example = "60500")
	long paidTotal,

	@Schema(description = "정산 목록. 최신 생성 순")
	PageResponse<SettlementResponse> settlements) {
}
