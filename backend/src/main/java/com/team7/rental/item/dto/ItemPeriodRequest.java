package com.team7.rental.item.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 대여 가능 기간 한 구간 [startDate, endDate] (양 끝 포함). 구간 사이 규칙은 {@link ValidItemInput} */
public record ItemPeriodRequest(
	@Schema(description = "시작일 (포함)", example = "2026-11-01") @NotNull
	LocalDate startDate,

	@Schema(description = "종료일 (포함)", example = "2026-11-30") @NotNull
	LocalDate endDate) {

	/** 검증 메시지용 "2026-11-01 ~ 2026-11-30" */
	String display() {
		return startDate + " ~ " + endDate;
	}
}
