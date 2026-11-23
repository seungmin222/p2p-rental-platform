package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관심 추가·해제 결과 (REQ-022). 처리 뒤의 현재 상태다 */
public record WishResponse(
	@Schema(description = "물품 ID", example = "101")
	Long itemId,

	@Schema(description = "처리 뒤 내가 이 물품을 관심 목록에 담았는지")
	boolean isWished,

	@Schema(description = "처리 뒤 물품의 관심 수", example = "6")
	int wishCount) {
}
