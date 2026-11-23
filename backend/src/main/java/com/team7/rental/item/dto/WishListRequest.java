package com.team7.rental.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 내 관심 목록 쿼리 파라미터 (REQ-022). 페이지 크기는 policies.md wish.page로 고정이고 size를 받지 않는다.
 *
 * @param page 생략하면 0
 */
public record WishListRequest(
	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public WishListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
