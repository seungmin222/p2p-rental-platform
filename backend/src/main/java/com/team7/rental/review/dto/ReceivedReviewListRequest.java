package com.team7.rental.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 받은 후기 목록 쿼리 파라미터 (REQ-034). 페이지 크기는 받지 않는다(policies.md review.page).
 *
 * @param page 생략하면 0
 */
public record ReceivedReviewListRequest(
	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public ReceivedReviewListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
