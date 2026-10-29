package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.RentalHistoryTab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 빌린 내역 쿼리 파라미터 (REQ-019). 페이지 크기는 받지 않는다(policies.md rental-history.page).
 *
 * @param tab 생략하면 ONGOING
 * @param page 생략하면 0
 */
public record RentalHistoryRequest(
	@Schema(description = "조회 역할. 빌린 내역은 renter만 허용한다(빌려준 쪽은 REQ-020)", example = "renter", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Pattern(regexp = "renter")
	String role,

	@Schema(description = "탭. 생략하면 ONGOING", defaultValue = "ONGOING")
	RentalHistoryTab tab,

	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public RentalHistoryRequest {
		if (tab == null) {
			tab = RentalHistoryTab.ONGOING;
		}
		if (page == null) {
			page = 0;
		}
	}
}
