package com.team7.rental.payment.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementTest {

	private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 11, 10);

	@Test
	@DisplayName("지급 예정으로 만들고 금액을 그대로 담는다")
	void schedule() {
		Settlement settlement = scheduled();

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
		assertThat(settlement.getGrossAmount()).isEqualTo(45_000);
		assertThat(settlement.getFeeAmount()).isEqualTo(4_500);
		assertThat(settlement.getDeductionAmount()).isEqualTo(20_000);
		assertThat(settlement.getPayoutAmount()).isEqualTo(60_500);
		assertThat(settlement.getPaidAt()).isNull();
	}

	@Test
	@DisplayName("지급 예정일 당일부터 지급 대상이고 PAID와 지급 시각을 남긴다")
	void pay() {
		Settlement settlement = scheduled();
		LocalDateTime now = SCHEDULED_DATE.atTime(0, 30);

		assertThat(settlement.isPayable(SCHEDULED_DATE.minusDays(1))).isFalse();
		assertThat(settlement.isPayable(SCHEDULED_DATE)).isTrue();
		settlement.pay(now);

		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
		assertThat(settlement.getPaidAt()).isEqualTo(now);
		assertThat(settlement.isPayable(SCHEDULED_DATE)).isFalse();
	}

	@Test
	@DisplayName("예정일 전이거나 이미 지급했으면 지급할 수 없다")
	void payRejected() {
		Settlement early = scheduled();
		Settlement paid = scheduled();
		paid.pay(SCHEDULED_DATE.atStartOfDay());

		assertThatThrownBy(() -> early.pay(SCHEDULED_DATE.minusDays(1).atStartOfDay()))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> paid.pay(SCHEDULED_DATE.atStartOfDay()))
			.isInstanceOf(IllegalStateException.class);
	}

	private Settlement scheduled() {
		return Settlement.schedule(1L, 2L, SettlementSource.COMPLETED,
			SettlementAmount.ofCompletion(30_000, 0, 15_000, 20_000, 10), SCHEDULED_DATE);
	}
}
