package com.team7.rental.item.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemAvailablePeriodTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final LocalDate END = LocalDate.of(2026, 11, 10);

	private final ItemAvailablePeriod period = ItemAvailablePeriod.create(1L, START, END);

	@Test
	@DisplayName("가능 기간의 시작일부터 종료일까지 전체는 포함된다")
	void coversWholePeriod() {
		// then
		assertThat(period.isCovering(START, END)).isTrue();
	}

	@Test
	@DisplayName("시작일 하루, 종료일 하루짜리 기간도 포함된다")
	void coversSingleDayAtEdges() {
		// then
		assertThat(period.isCovering(START, START)).isTrue();
		assertThat(period.isCovering(END, END)).isTrue();
	}

	@Test
	@DisplayName("안쪽 기간은 포함된다")
	void coversInside() {
		// then
		assertThat(period.isCovering(START.plusDays(2), END.minusDays(2))).isTrue();
	}

	@Test
	@DisplayName("시작일 하루 전부터면 포함되지 않는다")
	void notCoverBeforeStart() {
		// then
		assertThat(period.isCovering(START.minusDays(1), END)).isFalse();
		assertThat(period.isCovering(START.minusDays(1), START.minusDays(1))).isFalse();
	}

	@Test
	@DisplayName("종료일 하루 뒤까지면 포함되지 않는다")
	void notCoverAfterEnd() {
		// then
		assertThat(period.isCovering(START, END.plusDays(1))).isFalse();
		assertThat(period.isCovering(END.plusDays(1), END.plusDays(1))).isFalse();
	}
}
