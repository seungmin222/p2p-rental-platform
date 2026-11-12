package com.team7.rental.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 카테고리 추천 결과. 참고용이며 사용자가 고칠 수 있다")
public record AiCategoryResponse(
	@Schema(description = "결과 상태. DONE이 아니면 나머지 필드는 null")
	AiCategoryStatus status,
	@Schema(description = "추천 카테고리 ID (물품 등록 categoryId에 그대로 쓴다)", example = "1")
	Long categoryId,
	@Schema(description = "추천 카테고리 code (활성 카테고리 중 하나, 맞는 것이 없으면 ETC)", example = "CAMPING")
	String categoryCode,
	@Schema(description = "추천 카테고리 이름", example = "캠핑·아웃도어")
	String categoryName,
	@Schema(description = "categoryCode가 ETC일 때 AI가 낸 새 카테고리 후보 이름. 없으면 null", example = "악기")
	String suggestedCategoryName) {

	public static AiCategoryResponse done(Long categoryId, String categoryCode, String categoryName,
		String suggestedCategoryName) {
		return new AiCategoryResponse(AiCategoryStatus.DONE, categoryId, categoryCode, categoryName,
			suggestedCategoryName);
	}

	public static AiCategoryResponse empty(AiCategoryStatus status) {
		return new AiCategoryResponse(status, null, null, null, null);
	}
}
