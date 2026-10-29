package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 권장 보증금 조회 쿼리 파라미터 (REQ-006). itemPrice 범위는 {@link ValidItemInput}(item.item-price.max) */
@ValidItemInput
public record ItemDepositGuideRequest(
	@Schema(description = "카테고리 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
	Long categoryId,

	@Schema(description = "물품 가격(원). 0 ~ policies.md item.item-price.max", example = "200000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
	Integer itemPrice) {
}
