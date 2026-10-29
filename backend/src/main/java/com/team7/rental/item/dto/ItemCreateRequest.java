package com.team7.rental.item.dto;

import java.util.List;

import com.team7.rental.item.entity.ItemCondition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 물품 등록 multipart의 request part (REQ-006). 이미지는 images 파일 파트로 따로 받는다.
 * 글자 수·금액 범위·기간 규칙은 {@link ValidItemInput}(policies.md item.*), 활성 카테고리 여부는 서비스가 검사한다.
 */
@ValidItemInput
public record ItemCreateRequest(
	@Schema(description = "물품 이름. 최대 글자 수는 policies.md item.name.max", example = "4인용 거실형 텐트") @NotBlank
	String name,

	@Schema(description = "설명. 최대 글자 수는 policies.md item.description.max", example = "한 번 사용했어요. 팩·스트링 모두 있습니다.") @NotBlank
	String description,

	@Schema(description = "카테고리 ID (활성 카테고리)", example = "1") @NotNull
	Long categoryId,

	@Schema(description = "상태 등급") @NotNull
	ItemCondition condition,

	@Schema(description = "일 대여료(원). 0원(무료) 또는 policies.md item.price-per-day 범위", example = "15000") @NotNull
	Integer pricePerDay,

	@Schema(description = "물품 가격(원). 권장 보증금 계산용이며 대여자에게 공개하지 않는다. 0 ~ policies.md item.item-price.max", example = "200000") @NotNull
	Integer itemPrice,

	@Schema(description = "보증금(원). 0원 또는 policies.md item.deposit 범위", example = "50000") @NotNull
	Integer deposit,

	@Schema(description = "대여 가능 기간. 1개 이상, 시작일 ≥ 오늘, 시작일 ≤ 종료일, 구간끼리 겹침 불가") @NotEmpty
	List<@NotNull @Valid ItemPeriodRequest> availablePeriods,

	/** items.address VARCHAR(200) */
	@Schema(description = "거래 장소 주소 (카카오맵에서 선택)", example = "인천 연수구 송도동 24-5") @NotBlank @Size(max = 200)
	String address,

	@Schema(description = "거래 장소 위도", example = "37.3925") @NotNull @DecimalMin("-90.0") @DecimalMax("90.0")
	Double latitude,

	@Schema(description = "거래 장소 경도", example = "126.6390") @NotNull @DecimalMin("-180.0") @DecimalMax("180.0")
	Double longitude) {
}
