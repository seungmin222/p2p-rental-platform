package com.team7.rental.payment.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 영업일 +2 (settlement.payout-delay, 주말만 제외). 2026-11-02는 월요일 */
class BusinessDaysTest {

	@ParameterizedTest(name = "{0} + 2영업일 = {1}")
	@CsvSource({
		"2026-11-02, 2026-11-04", // 월 → 수
		"2026-11-05, 2026-11-09", // 목 → 월
		"2026-11-06, 2026-11-10", // 금 → 화
		"2026-11-07, 2026-11-10", // 토 → 화
		"2026-11-08, 2026-11-10" // 일 → 화
	})
	void plusTwo(LocalDate from, LocalDate expected) {
		assertThat(BusinessDays.plus(from, 2)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} + 0영업일 = 그날")
	@CsvSource({"2026-11-07"})
	void plusZero(LocalDate from) {
		assertThat(BusinessDays.plus(from, 0)).isEqualTo(from);
	}
}
