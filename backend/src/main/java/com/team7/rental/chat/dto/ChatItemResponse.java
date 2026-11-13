package com.team7.rental.chat.dto;

import com.team7.rental.item.dto.ItemSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅방의 물품 요약 (REQ-028). 삭제된 물품도 담고 isDeleted로 표시한다(화면이 "삭제된 물품"으로 표시) */
public record ChatItemResponse(
	@Schema(description = "물품 ID", example = "7")
	Long itemId,

	@Schema(description = "물품 이름", example = "전동 드릴")
	String name,

	@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없으면 null", nullable = true)
	String thumbnailUrl,

	@Schema(description = "삭제(DELETED)된 물품이면 true")
	boolean isDeleted) {

	public static ChatItemResponse of(ItemSummary item) {
		return new ChatItemResponse(item.itemId(), item.name(), item.thumbnailUrl(), item.isDeleted());
	}
}
