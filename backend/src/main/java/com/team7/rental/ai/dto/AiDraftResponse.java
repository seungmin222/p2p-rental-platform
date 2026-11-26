package com.team7.rental.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 등록 보조 결과 (REQ-067). 등록 폼에 미리 채우는 초안이며 사용자가 고친 뒤 등록한다")
public record AiDraftResponse(
	@Schema(description = "결과 상태. DONE이 아니면 나머지 필드는 모두 null")
	AiDraftStatus status,
	@Schema(description = "카테고리 ID (물품 등록 categoryId에 그대로 쓴다). DONE이면 항상 있다", example = "1")
	Long categoryId,
	@Schema(description = "카테고리 code (활성 카테고리 중 하나, 맞는 것이 없으면 ETC)", example = "CAMPING")
	String categoryCode,
	@Schema(description = "카테고리 이름", example = "캠핑·아웃도어")
	String categoryName,
	@Schema(description = "categoryCode가 ETC일 때 AI가 낸 새 카테고리 후보 이름. 없으면 null", example = "악기")
	String suggestedCategoryName,
	@Schema(description = "제목 초안 (policies.md item.name.max 이내로 자름). 만들지 못하면 null", example = "코베아 4인용 돔 텐트")
	String title,
	@Schema(description = "설명 초안 (policies.md item.description.max 이내로 자름). 만들지 못하면 null", example = "4인용 돔 텐트입니다. 폴대·팩·스트링이 함께 있습니다.")
	String description,
	@Schema(description = "AI가 추정한 물품 가격(원, 비공개 itemPrice 초안). 0 ~ item.item-price.max를 벗어나거나 추정하지 못하면 null", example = "200000")
	Integer itemPrice,
	@Schema(description = "AI가 제안한 일 대여료(원). 0원 또는 item.price-per-day 범위가 아니거나 제안하지 못하면 null", example = "15000")
	Integer pricePerDay,
	@Schema(description = "권장 최소 보증금(원). itemPrice에 GET /api/items/deposit-guide와 같은 규칙을 적용한 값이고, itemPrice가 null이면 null", example = "60000")
	Integer minDeposit,
	@Schema(description = "권장 최대 보증금(원). minDeposit과 같은 규칙", example = "100000")
	Integer maxDeposit) {

	public static AiDraftResponse empty(AiDraftStatus status) {
		return new AiDraftResponse(status, null, null, null, null, null, null, null, null, null, null);
	}
}
