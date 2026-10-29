package com.team7.rental.item.dto;

import java.time.LocalDateTime;

import com.team7.rental.item.entity.ItemStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 물품 관리 카드 (REQ-020, SCR-12). 물품 카드 + 상태·조회 수·다가오는 예약 건수 */
public record MyItemResponse(
	@Schema(description = "물품 ID", example = "107")
	Long itemId,

	@Schema(description = "물품 이름", example = "보드게임 세트")
	String name,

	@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없으면 null", example = "/files/items/107/thumb_a1b2c3.jpg")
	String thumbnailUrl,

	@Schema(description = "물품 동네 이름", example = "송도1동")
	String regionName,

	@Schema(description = "일 대여료(원)", example = "3000")
	int pricePerDay,

	@Schema(description = "보증금(원)", example = "10000")
	int deposit,

	@Schema(description = "물품 상태 (AVAILABLE 또는 HIDDEN)")
	ItemStatus status,

	@Schema(description = "조회 수", example = "42")
	int viewCount,

	@Schema(description = "관심 수", example = "5")
	int wishCount,

	@Schema(description = "채팅 수", example = "3")
	int chatCount,

	@Schema(description = "다가오는 예약 건수: RESERVED·PENDING_PAYMENT(결제 만료 전) 중 시작일 ≥ 오늘", example = "1")
	int upcomingRentalCount,

	@Schema(description = "지금 대여 중(RENTING·OVERDUE 대여 있음)이면 true")
	boolean isRenting,

	@Schema(description = "등록 또는 끌어올리기 시각", example = "2026-10-29T10:00:00")
	LocalDateTime bumpedAt) {
}
