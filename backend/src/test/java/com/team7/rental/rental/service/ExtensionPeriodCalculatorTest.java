package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;

/** 연장 가능한 최대 종료일 (REQ-038). 기존 종료일 = 11/10, 한 번에 최대 7일 */
class ExtensionPeriodCalculatorTest {

	private static final LocalDate PREV_END = LocalDate.of(2026, 11, 10);
	private static final int MAX_DAYS = 7;
	private static final List<AvailablePeriod> NOVEMBER = List.of(
		new AvailablePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));

	@Test
	@DisplayName("가로막는 것이 없으면 기존 종료일 + 7일이다")
	void cappedByMaxDays() {
		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, NOVEMBER, List.of()))
			.contains(LocalDate.of(2026, 11, 17));
	}

	@Test
	@DisplayName("가능 기간이 먼저 끝나면 그 끝날이다")
	void cappedByAvailablePeriod() {
		List<AvailablePeriod> periods = List.of(
			new AvailablePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13)));

		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, periods, List.of()))
			.contains(LocalDate.of(2026, 11, 13));
	}

	@Test
	@DisplayName("뒤 예약이 있으면 그 시작일 전날이다 (경계 동일일: 뒤 예약 시작일까지는 연장할 수 없다)")
	void cappedByNextOccupation() {
		List<OccupiedPeriod> occupied = List.of(
			new OccupiedPeriod(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 16)));

		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, NOVEMBER, occupied))
			.contains(LocalDate.of(2026, 11, 13));
	}

	@Test
	@DisplayName("여러 점유가 있으면 가장 먼저 시작하는 것을 기준으로 한다")
	void earliestOccupationWins() {
		List<OccupiedPeriod> occupied = List.of(
			new OccupiedPeriod(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 20)),
			new OccupiedPeriod(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13)));

		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, NOVEMBER, occupied))
			.contains(LocalDate.of(2026, 11, 11));
	}

	@Test
	@DisplayName("기존 종료일 다음 날부터 점유돼 있거나 가능 기간 밖이면 하루도 연장할 수 없다")
	void noDayAvailable() {
		List<OccupiedPeriod> nextDay = List.of(
			new OccupiedPeriod(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)));
		List<AvailablePeriod> endsAtPrevEnd = List.of(new AvailablePeriod(LocalDate.of(2026, 11, 1), PREV_END));

		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, NOVEMBER, nextDay)).isEmpty();
		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, endsAtPrevEnd, List.of())).isEmpty();
	}

	@Test
	@DisplayName("이어진 다음 가능 기간으로는 넘어가지 않는다(가능 기간 하나 안에서만, 예약과 같은 규칙)")
	void doesNotSpanAdjacentPeriods() {
		List<AvailablePeriod> periods = List.of(
			new AvailablePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 12)),
			new AvailablePeriod(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30)));

		assertThat(ExtensionPeriodCalculator.maxEndDate(PREV_END, MAX_DAYS, periods, List.of()))
			.contains(LocalDate.of(2026, 11, 12));
	}
}
