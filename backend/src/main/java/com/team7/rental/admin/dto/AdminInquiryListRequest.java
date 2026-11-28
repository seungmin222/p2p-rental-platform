package com.team7.rental.admin.dto;

import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 관리자 문의 목록 쿼리 파라미터 (REQ-057). 페이지 크기는 받지 않는다(policies.md inquiry.page).
 *
 * @param page 생략하면 0
 */
public record AdminInquiryListRequest(
	@Schema(description = "처리 상태 조건. 생략하면 전체")
	InquiryStatus status,

	@Schema(description = "문의 종류 조건. 생략하면 전체")
	InquiryType type,

	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public AdminInquiryListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
