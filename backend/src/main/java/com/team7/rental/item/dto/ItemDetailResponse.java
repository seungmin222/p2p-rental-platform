package com.team7.rental.item.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.item.entity.ItemStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 물품 상세 (REQ-011, SCR-06). 물품 가격(itemPrice)은 담지 않는다(소유자 수정 화면 GET /api/members/me/items/{itemId}만 예외).
 * 대여 가능 기간·예약 현황은 캘린더 API(REQ-012)로 본다. 제공자 신뢰 점수는 REQ-054에서 추가한다.
 */
public record ItemDetailResponse(
	@Schema(description = "물품 ID", example = "101")
	Long itemId,

	@Schema(description = "물품 이름", example = "4인용 거실형 텐트")
	String name,

	@Schema(description = "설명", example = "한 번 사용했어요. 팩·스트링 모두 있습니다.")
	String description,

	@Schema(description = "이미지 (0번이 대표)")
	List<ItemImageResponse> images,

	@Schema(description = "카테고리")
	Category category,

	@Schema(description = "상태 등급")
	ItemCondition condition,

	@Schema(description = "일 대여료(원). 0이면 무료", example = "15000")
	int pricePerDay,

	@Schema(description = "보증금(원)", example = "50000")
	int deposit,

	@Schema(description = "물품 동네(등록 시 대표 동네) 이름", example = "송도1동")
	String regionName,

	@Schema(description = "거래 장소 주소", example = "인천 연수구 송도동 24-5")
	String address,

	@Schema(description = "거래 장소 위도", example = "37.3925")
	double latitude,

	@Schema(description = "거래 장소 경도", example = "126.6390")
	double longitude,

	@Schema(description = "물품 상태. HIDDEN은 소유자에게만 보인다")
	ItemStatus status,

	@Schema(description = "등록 또는 끌어올리기 시각", example = "2026-10-29T10:00:00")
	LocalDateTime bumpedAt,

	@Schema(description = "조회 수", example = "42")
	int viewCount,

	@Schema(description = "관심 수", example = "5")
	int wishCount,

	@Schema(description = "채팅 수", example = "3")
	int chatCount,

	@Schema(description = "제공자 요약")
	Owner owner,

	@Schema(description = "조회한 회원이 제공자면 true → 예약 대신 수정·관리 버튼. 비회원은 false")
	boolean isOwner,

	@Schema(description = "조회한 회원이 관심 추가했으면 true. 관심 기능(REQ-022) 전에는 항상 false")
	boolean isWished) {

	public record Category(
		@Schema(description = "카테고리 ID", example = "1")
		Long categoryId,

		@Schema(description = "이름", example = "캠핑·아웃도어")
		String name) {
	}

	public record Owner(
		@Schema(description = "제공자 회원 ID", example = "2")
		Long memberId,

		@Schema(description = "닉네임", example = "캠핑조아")
		String nickname,

		@Schema(description = "프로필 이미지 URL. 없으면 null", example = "/files/profiles/2/d4e5f6.jpg")
		String profileImageUrl,

		@Schema(description = "제공자의 대표 동네 이름. 없으면 null", example = "송도1동")
		String regionName) {
	}
}
