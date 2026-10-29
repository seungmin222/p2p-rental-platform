package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 물품 이미지. 목록 순서 = sortOrder(0번이 대표) */
public record ItemImageResponse(
	@Schema(description = "이미지 ID (수정 시 imageOrder의 imageId)", example = "12")
	Long imageId,

	@Schema(description = "원본 URL", example = "/files/items/101/a1b2c3.jpg")
	String url,

	@Schema(description = "썸네일 URL (가로 policies.md thumbnail.width)", example = "/files/items/101/thumb_a1b2c3.jpg")
	String thumbnailUrl) {
}
