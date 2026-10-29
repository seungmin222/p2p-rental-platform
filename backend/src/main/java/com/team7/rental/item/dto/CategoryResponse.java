package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 카테고리 (REQ-006). 활성 카테고리만 sortOrder 순으로 내려간다 */
public record CategoryResponse(
	@Schema(description = "카테고리 ID", example = "1")
	Long categoryId,

	@Schema(description = "이름", example = "캠핑·아웃도어")
	String name,

	@Schema(description = "표시 순서 (작을수록 앞)", example = "1")
	int sortOrder) {
}
