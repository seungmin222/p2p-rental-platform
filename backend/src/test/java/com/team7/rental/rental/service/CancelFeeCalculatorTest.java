package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;

/** 취소 수수료 (REQ-015, policies.md §2 취소 수수료 표). 정책 값은 yml과 같은 30% / 3일 전으로 둔다 */
class CancelFeeCalculatorTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int PRICE_PER_DAY = 10_000;
	private static final int DEPOSIT = 50_000;

	private final CancelFeeCalculator calculator = new CancelFeeCalculator(
		new PolicyProperties(null, null, null, null, null, null, new PolicyProperties.CancelFee(30, 3), null, null,
			null, null, null, null, null, null, null, null));

	@Test
	@DisplayName("대여자가 시작일 3일 전까지 취소하면 수수료가 없다")
	void renterFreeUntilThreeDaysBefore() {
		// given: 대여료 30,000원
		Rental rental = reserved(PRICE_PER_DAY);

		// then
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(10))).isZero();
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(3))).isZero();
	}

	@Test
	@DisplayName("대여자가 시작일 2일 전·전날 취소하면 대여료의 30%다")
	void renterFeeTwoDaysBeforeAndDayBefore() {
		// given: 대여료 30,000원
		Rental rental = reserved(PRICE_PER_DAY);

		// then
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(2))).isEqualTo(9_000);
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(1))).isEqualTo(9_000);
	}

	@Test
	@DisplayName("대여 예정의 시작일 당일·이후 단독 취소는 계산 대상이 아니다(validateCancelable에서 막힌다)")
	void soleCancelOnOrAfterStartIsNotCalculated() {
		// given
		Rental rental = reserved(PRICE_PER_DAY);

		// then
		assertThatThrownBy(() -> calculator.calculate(rental, CancelType.RENTER, START))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> calculator.calculate(rental, CancelType.OWNER, START.plusDays(1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("수수료는 원 미만을 절사한다")
	void truncateBelowOneWon() {
		// given: 3,333원 × 3일 = 9,999원 → 30% = 2,999.7원
		Rental rental = reserved(3_333);

		// then
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(1))).isEqualTo(2_999);
	}

	@Test
	@DisplayName("제공자 취소는 수수료가 없다")
	void ownerNoFee() {
		// given
		Rental rental = reserved(PRICE_PER_DAY);

		// then
		assertThat(calculator.calculate(rental, CancelType.OWNER, START.minusDays(1))).isZero();
	}

	@Test
	@DisplayName("결제 대기 취소는 시작일이 지났어도 수수료가 없다")
	void pendingPaymentNoFee() {
		// given: 시작일 전날 23:59 생성
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT,
			START.minusDays(1).atTime(23, 59).plusMinutes(15));

		// then
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(1))).isZero();
		assertThat(calculator.calculate(rental, CancelType.RENTER, START)).isZero();
		assertThat(calculator.calculate(rental, CancelType.OWNER, START)).isZero();
	}

	@Test
	@DisplayName("양측 동의 취소는 수수료가 없다")
	void mutualNoFee() {
		// given
		Rental rental = reserved(PRICE_PER_DAY);

		// then
		assertThat(calculator.calculate(rental, CancelType.MUTUAL, START.plusDays(1))).isZero();
	}

	@Test
	@DisplayName("대여료가 0원이면 수수료 구간이어도 0원이다")
	void freeRental() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, 0, 0);

		// then
		assertThat(calculator.calculate(rental, CancelType.RENTER, START.minusDays(1))).isZero();
	}

	private Rental reserved(int pricePerDay) {
		return Rental.createReserved(1L, 2L, 3L, START, END, pricePerDay, DEPOSIT);
	}
}
