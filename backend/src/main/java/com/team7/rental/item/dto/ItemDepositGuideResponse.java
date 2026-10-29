package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 권장 보증금 범위 (REQ-006) = [물품 가격 × 최소 비율, 물품 가격 × 최대 비율] (카테고리별 비율 policies.md §4). 안내만 하고, 벗어나도 등록할 수 있다.
 */
public record ItemDepositGuideResponse(
	@Schema(description = "권장 최소 보증금(원)", example = "60000")
	int minDeposit,

	@Schema(description = "권장 최대 보증금(원)", example = "100000")
	int maxDeposit) {
}
