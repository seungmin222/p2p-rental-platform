package com.team7.rental.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 정산 내역 쿼리 파라미터 (REQ-039). 페이지 크기는 받지 않는다(policies.md settlement.page).
 *
 * @param page 생략하면 0
 */
public record SettlementListRequest(
	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public SettlementListRequest {
		if (page == null) {
			page = 0;
		}
	}
}
