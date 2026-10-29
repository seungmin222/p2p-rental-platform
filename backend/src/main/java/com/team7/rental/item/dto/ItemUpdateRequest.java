package com.team7.rental.item.dto;

import java.util.List;

import com.team7.rental.item.entity.ItemCondition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 물품 수정 multipart의 request part (REQ-007). 모든 필드가 선택이고 null이면 바꾸지 않는다. 보낸 값의 규칙은 등록과 같다
 * ({@link ValidItemInput}). 단, 대여 가능 기간은 진행 중인 대여를 덮는 구간을 유지할 수 있게 시작일 ≥ 오늘을 검사하지 않는다.
 */
@ValidItemInput
public record ItemUpdateRequest(
	@Schema(description = "물품 이름. 최대 글자 수는 policies.md item.name.max", example = "4인용 거실형 텐트")
	String name,

	@Schema(description = "설명. 최대 글자 수는 policies.md item.description.max", example = "한 번 사용했어요. 팩·스트링 모두 있습니다.")
	String description,

	@Schema(description = "카테고리 ID (활성 카테고리)", example = "1")
	Long categoryId,

	@Schema(description = "상태 등급")
	ItemCondition condition,

	@Schema(description = "일 대여료(원). 0원(무료) 또는 policies.md item.price-per-day 범위. 이후 신규 예약부터 적용", example = "15000")
	Integer pricePerDay,

	@Schema(description = "물품 가격(원). 0 ~ policies.md item.item-price.max", example = "200000")
	Integer itemPrice,

	@Schema(description = "보증금(원). 0원 또는 policies.md item.deposit 범위. 이후 신규 예약부터 적용", example = "50000")
	Integer deposit,

	@Schema(description = "대여 가능 기간 전체(보낸 목록으로 교체). 보내면 1개 이상, 시작일 ≤ 종료일, 구간끼리 겹침 불가")
	List<@NotNull @Valid ItemPeriodRequest> availablePeriods,

	@Schema(description = "이미지 순서 전체. 보내면 이 목록이 곧 새 이미지 목록이다(순서 = sortOrder, 0번이 대표). "
		+ "목록에 없는 기존 이미지는 삭제된다. null이면 이미지를 바꾸지 않는다")
	List<@NotNull @Valid ImageOrder> imageOrder,

	/** items.address VARCHAR(200) */
	@Schema(description = "거래 장소 주소", example = "인천 연수구 송도동 24-5") @Size(max = 200)
	String address,

	@Schema(description = "거래 장소 위도. 주소를 바꾸면 위도·경도도 함께 보낸다", example = "37.3925") @DecimalMin("-90.0") @DecimalMax("90.0")
	Double latitude,

	@Schema(description = "거래 장소 경도. 주소를 바꾸면 위도·경도도 함께 보낸다", example = "126.6390") @DecimalMin("-180.0") @DecimalMax("180.0")
	Double longitude) {

	/** 이미지 한 칸. imageId(유지할 기존 이미지)와 newImageIndex(새 파일) 중 정확히 하나 */
	public record ImageOrder(
		@Schema(description = "유지할 기존 이미지 ID", example = "12")
		Long imageId,

		@Schema(description = "새 이미지: images 파일 파트에서의 순번 (0부터)", example = "0") @PositiveOrZero
		Integer newImageIndex) {
	}
}
