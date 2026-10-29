package com.team7.rental.item.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 물품 카드 (REQ-009 피드, REQ-010 검색). 물품 가격(itemPrice)은 담지 않는다 */
public record ItemSummaryResponse(
	@Schema(description = "물품 ID", example = "101")
	Long itemId,

	@Schema(description = "물품 이름", example = "4인용 거실형 텐트")
	String name,

	@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없으면 null", example = "/files/items/101/thumb_a1b2c3.jpg")
	String thumbnailUrl,

	@Schema(description = "물품 동네(등록 시 대표 동네) 이름", example = "송도1동")
	String regionName,

	@Schema(description = "일 대여료(원). 0이면 무료", example = "15000")
	int pricePerDay,

	@Schema(description = "보증금(원)", example = "50000")
	int deposit,

	@Schema(description = "관심 수", example = "5")
	int wishCount,

	@Schema(description = "채팅 수", example = "3")
	int chatCount,

	@Schema(description = "지금 대여 중(RENTING·OVERDUE 대여 있음)이면 true → \"대여 중\" 배지")
	boolean isRenting,

	@Schema(description = "정렬·경과 시간 기준 시각 (등록 또는 끌어올리기)", example = "2026-10-29T10:00:00")
	LocalDateTime bumpedAt) {
}
