package com.team7.rental.item.dto;

import java.time.LocalDate;

/** 대여 가능 기간 [startDate, endDate] (양 끝 포함) */
public record AvailablePeriod(LocalDate startDate, LocalDate endDate) {

	/** [start, end]가 이 기간 안에 완전히 들어가는지 */
	public boolean isCovering(LocalDate start, LocalDate end) {
		return !start.isBefore(startDate) && !end.isAfter(endDate);
	}

	public boolean contains(LocalDate date) {
		return isCovering(date, date);
	}
}
