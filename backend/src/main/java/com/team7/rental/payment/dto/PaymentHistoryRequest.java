package com.team7.rental.payment.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 결제·보증금 내역 쿼리 파라미터 (REQ-033). 페이지 크기는 받지 않는다(policies.md payment-history.page). 기간 기본값과 from &gt; to 검사는
 * 오늘 날짜가 필요해 서비스가 한다.
 *
 * @param type 생략하면 ALL
 * @param from 생략하면 to − payment-history.default-range
 * @param to 생략하면 오늘
 * @param page 생략하면 0
 */
public record PaymentHistoryRequest(
	@Schema(description = "구분. 생략하면 ALL", defaultValue = "ALL")
	PaymentHistoryType type,

	@Schema(description = "조회 시작일(포함). 생략하면 to에서 policies.md payment-history.default-range(3개월) 전", example = "2026-08-10")
	LocalDate from,

	@Schema(description = "조회 종료일(포함). 생략하면 오늘. from보다 빠르면 400", example = "2026-11-10")
	LocalDate to,

	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public PaymentHistoryRequest {
		if (type == null) {
			type = PaymentHistoryType.ALL;
		}
		if (page == null) {
			page = 0;
		}
	}
}
