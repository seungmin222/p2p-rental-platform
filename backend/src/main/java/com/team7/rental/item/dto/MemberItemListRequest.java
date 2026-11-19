package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 사용자 프로필의 등록 물품 쿼리 파라미터 (REQ-021). 페이지 크기는 policies.md profile-items.page로 고정이고 size를 받지 않는다.
 *
 * @param page 생략하면 0
 */
public record MemberItemListRequest(
	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public MemberItemListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
