package com.team7.rental.payment.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** 영업일 계산 (settlement.payout-delay). 주말만 제외하고 공휴일은 반영하지 않는다(open-issues #10) */
public final class BusinessDays {

	private BusinessDays() {}

	/** from 다음 날부터 세어 days번째 영업일. 예: 금·토·일 + 2 = 화, 목 + 2 = 월 */
	public static LocalDate plus(LocalDate from, int days) {
		if (days < 0) {
			throw new IllegalArgumentException("영업일 수는 0 이상이어야 합니다: " + days);
		}
		LocalDate date = from;
		int counted = 0;
		while (counted < days) {
			date = date.plusDays(1);
			if (!isWeekend(date)) {
				counted++;
			}
		}
		return date;
	}

	private static boolean isWeekend(LocalDate date) {
		return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
	}
}
