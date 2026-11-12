package com.team7.rental.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 내 문의 목록 쿼리 파라미터 (REQ-063). 페이지 크기는 받지 않는다(policies.md inquiry.page).
 *
 * @param page 생략하면 0
 */
public record InquiryListRequest(
	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public InquiryListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
