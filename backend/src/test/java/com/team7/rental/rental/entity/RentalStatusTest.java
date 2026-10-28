package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RentalStatusTest {

	@Test
	@DisplayName("기간 점유 상태는 PENDING_PAYMENT·RESERVED·RENTING·OVERDUE 4개뿐이다")
	void activeForPeriod() {
		// then
		assertThat(RentalStatus.ACTIVE_FOR_PERIOD).containsExactlyInAnyOrder(RentalStatus.PENDING_PAYMENT,
			RentalStatus.RESERVED, RentalStatus.RENTING, RentalStatus.OVERDUE);
		assertThat(RentalStatus.ACTIVE_FOR_PERIOD).doesNotContain(RentalStatus.RETURNED, RentalStatus.COMPLETED,
			RentalStatus.CANCELED);
	}

	@Test
	@DisplayName("기간 점유 상태 집합은 수정할 수 없다")
	void activeForPeriodIsUnmodifiable() {
		// then
		assertThatThrownBy(() -> RentalStatus.ACTIVE_FOR_PERIOD.add(RentalStatus.RETURNED))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> RentalStatus.ACTIVE_FOR_PERIOD.remove(RentalStatus.RESERVED))
			.isInstanceOf(UnsupportedOperationException.class);
	}
}
