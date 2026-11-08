package com.team7.rental.payment.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정산 금액 (docs/domains/payment.md REQ-039). 수수료율 10% */
class SettlementAmountTest {

	private static final int RATE = 10;

	@Test
	@DisplayName("거래 완료: 수수료는 대여료·연장료·연체료 합의 10%를 원 미만 절사한다")
	void completionFloorsFee() {
		SettlementAmount amount = SettlementAmount.ofCompletion(30_005, 0, 0, 0, RATE);

		assertThat(amount.gross()).isEqualTo(30_005);
		assertThat(amount.fee()).isEqualTo(3_000);
		assertThat(amount.payout()).isEqualTo(27_005);
	}

	@Test
	@DisplayName("거래 완료: 연체료와 손상 차감이 둘 다 있으면 연체료에만 수수료를 떼고 차감액은 그대로 더한다")
	void completionWithOverdueAndDeduction() {
		SettlementAmount amount = SettlementAmount.ofCompletion(30_000, 0, 15_000, 20_000, RATE);

		assertThat(amount.gross()).isEqualTo(45_000);
		assertThat(amount.fee()).isEqualTo(4_500);
		assertThat(amount.deduction()).isEqualTo(20_000);
		assertThat(amount.payout()).isEqualTo(45_000 - 4_500 + 20_000);
	}

	@Test
	@DisplayName("거래 완료: 연장료도 수수료 대상이다")
	void completionWithExtension() {
		SettlementAmount amount = SettlementAmount.ofCompletion(30_000, 14_000, 0, 0, RATE);

		assertThat(amount.gross()).isEqualTo(44_000);
		assertThat(amount.fee()).isEqualTo(4_400);
		assertThat(amount.payout()).isEqualTo(39_600);
	}

	@Test
	@DisplayName("무료 대여의 차감만 있는 건: gross 0, 지급액 = 차감액")
	void completionDeductionOnly() {
		SettlementAmount amount = SettlementAmount.ofCompletion(0, 0, 0, 10_000, RATE);

		assertThat(amount.fee()).isZero();
		assertThat(amount.payout()).isEqualTo(10_000);
	}

	@Test
	@DisplayName("수수료 있는 취소: gross = 취소 수수료, 차감 없음")
	void cancelFee() {
		SettlementAmount amount = SettlementAmount.ofCancelFee(9_009, RATE);

		assertThat(amount.gross()).isEqualTo(9_009);
		assertThat(amount.fee()).isEqualTo(900);
		assertThat(amount.deduction()).isZero();
		assertThat(amount.payout()).isEqualTo(8_109);
	}

	@Test
	@DisplayName("음수 금액은 받지 않는다")
	void negative() {
		assertThatThrownBy(() -> SettlementAmount.ofCompletion(0, 0, 0, -1, RATE))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
