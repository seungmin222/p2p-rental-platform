package com.team7.rental.item.dto;

import java.time.LocalDateTime;

import com.team7.rental.item.entity.ItemStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 관심 목록 카드 (REQ-022). 피드 카드(ItemSummaryResponse)에 물품 상태와 관심 추가 시각을 더했다. 물품 가격(itemPrice)은 담지 않는다.
 * HIDDEN·DELETED 물품은 itemId·status·wishedAt만 채우고 나머지는 null·0·false다.
 */
public record WishItemResponse(
	@Schema(description = "물품 ID", example = "101")
	Long itemId,

	@Schema(description = "물품 이름. HIDDEN·DELETED면 null", example = "4인용 거실형 텐트")
	String name,

	@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없거나 HIDDEN·DELETED면 null", example = "/files/items/101/thumb_a1b2c3.jpg")
	String thumbnailUrl,

	@Schema(description = "물품 동네(등록 시 대표 동네) 이름. HIDDEN·DELETED면 null", example = "송도1동")
	String regionName,

	@Schema(description = "일 대여료(원). 0이면 무료. HIDDEN·DELETED면 0", example = "15000")
	int pricePerDay,

	@Schema(description = "보증금(원). HIDDEN·DELETED면 0", example = "50000")
	int deposit,

	@Schema(description = "물품 상태. DELETED면 \"삭제된 물품\", HIDDEN이면 \"볼 수 없는 물품\"으로 표시한다(둘 다 상세는 404). 이때 itemId·status·wishedAt 외 필드는 null·0·false다")
	ItemStatus status,

	@Schema(description = "관심 수. HIDDEN·DELETED면 0", example = "5")
	int wishCount,

	@Schema(description = "채팅 수. HIDDEN·DELETED면 0", example = "3")
	int chatCount,

	@Schema(description = "지금 대여 중(RENTING·OVERDUE 대여 있음)이면 true → \"대여 중\" 배지. HIDDEN·DELETED면 false")
	boolean isRenting,

	@Schema(description = "관심 목록에 담은 시각", example = "2026-11-22T10:00:00")
	LocalDateTime wishedAt) {

	/** 숨김·삭제 물품 카드. 상세가 404인 물품이라 목록에서 구분·해제에 필요한 값만 남긴다 */
	public static WishItemResponse unavailable(Long itemId, ItemStatus status, LocalDateTime wishedAt) {
		return new WishItemResponse(itemId, null, null, null, 0, 0, status, 0, 0, false, wishedAt);
	}
}
