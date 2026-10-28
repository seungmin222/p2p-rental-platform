package com.team7.rental.rental.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 대여 캘린더 (REQ-012). 그 달의 모든 날짜를 1일부터 순서대로 담는다 */
public record RentalCalendarResponse(
	@Schema(description = "조회한 달", type = "string", example = "2026-10")
	YearMonth month,

	List<CalendarDay> days) {

	public record CalendarDay(
		@Schema(description = "날짜", example = "2026-10-01")
		LocalDate date,

		@Schema(description = "예약 가능 여부")
		CalendarDayStatus status) {
	}
}
