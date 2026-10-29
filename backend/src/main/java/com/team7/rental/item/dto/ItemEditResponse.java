package com.team7.rental.item.dto;

import java.time.LocalDate;
import java.util.List;

import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.item.entity.ItemStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소유자 수정 화면 (REQ-007). 물품 가격(itemPrice)을 담는 유일한 응답이다(item.md REQ-006 "소유자용 수정 화면 응답만 예외").
 * 수정 요청(ItemUpdateRequest)에 그대로 다시 보낼 수 있는 값만 담는다.
 */
public record ItemEditResponse(
	@Schema(description = "물품 ID", example = "101")
	Long itemId,

	@Schema(description = "물품 이름", example = "4인용 거실형 텐트")
	String name,

	@Schema(description = "설명", example = "한 번 사용했어요. 팩·스트링 모두 있습니다.")
	String description,

	@Schema(description = "카테고리 ID", example = "1")
	Long categoryId,

	@Schema(description = "상태 등급")
	ItemCondition condition,

	@Schema(description = "일 대여료(원)", example = "15000")
	int pricePerDay,

	@Schema(description = "물품 가격(원). 소유자에게만 보인다", example = "200000")
	int itemPrice,

	@Schema(description = "보증금(원)", example = "50000")
	int deposit,

	@Schema(description = "대여 가능 기간 (시작일 순)")
	List<Period> availablePeriods,

	@Schema(description = "이미지 (0번이 대표). 수정 시 imageOrder의 imageId로 쓴다")
	List<ItemImageResponse> images,

	@Schema(description = "거래 장소 주소", example = "인천 연수구 송도동 24-5")
	String address,

	@Schema(description = "거래 장소 위도", example = "37.3925")
	double latitude,

	@Schema(description = "거래 장소 경도", example = "126.6390")
	double longitude,

	@Schema(description = "물품 상태")
	ItemStatus status) {

	public record Period(
		@Schema(description = "시작일 (포함)", example = "2026-11-01")
		LocalDate startDate,

		@Schema(description = "종료일 (포함)", example = "2026-11-30")
		LocalDate endDate) {
	}
}
