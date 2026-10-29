package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 물품 등록 결과 (REQ-006) */
public record ItemCreateResponse(
	@Schema(description = "등록한 물품 ID", example = "101")
	Long itemId) {
}
