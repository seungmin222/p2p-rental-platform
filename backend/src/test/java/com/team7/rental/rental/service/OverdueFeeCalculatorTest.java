package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

/** 연체료 (REQ-023, policies.md §4). overdue-fee.rate = 150% */
class OverdueFeeCalculatorTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int PRICE_PER_DAY = 10_000;
	private static final int DEPOSIT = 50_000;

	private final OverdueFeeCalculator calculator = new OverdueFeeCalculator(
		new PolicyProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, null, null, null, new PolicyProperties.OverdueFee(150), null, null, null, null, null,
			null, null));

	@Test
	@DisplayName("반납 예정일 당일·이전 반납은 연체 0일, 0원이다")
	void zeroDays() {
		assertThat(OverdueFeeCalculator.overdueDays(END, END)).isZero();
		assertThat(OverdueFeeCalculator.overdueDays(END, END.minusDays(1))).isZero();
		assertThat(calculator.calculate(PRICE_PER_DAY, DEPOSIT, END, END)).isZero();
		assertThat(calculator.calculate(PRICE_PER_DAY, DEPOSIT, END, START)).isZero();
	}

	@Test
	@DisplayName("연체 1일이면 floor(일 대여료 × 1.5), 3일이면 그 3배다")
	void perDay() {
		assertThat(OverdueFeeCalculator.overdueDays(END, END.plusDays(1))).isEqualTo(1);
		assertThat(calculator.calculate(PRICE_PER_DAY, DEPOSIT, END, END.plusDays(1))).isEqualTo(15_000);
		assertThat(calculator.calculate(PRICE_PER_DAY, DEPOSIT, END, END.plusDays(3))).isEqualTo(45_000);
	}

	@Test
	@DisplayName("1일 연체료는 원 미만을 절사한 뒤 일수를 곱한다")
	void floorsPerDayBeforeMultiplying() {
		// 1,001 × 1.5 = 1,501.5 → 1,501원/일 × 2일 = 3,002원 (곱한 뒤 절사하면 3,003원)
		assertThat(calculator.calculate(1_001, DEPOSIT, END, END.plusDays(2))).isEqualTo(3_002);
	}

	@Test
	@DisplayName("보증금을 넘으면 보증금이 상한이다")
	void cappedByDeposit() {
		// 4일 × 15,000 = 60,000 > 50,000
		assertThat(calculator.calculate(PRICE_PER_DAY, DEPOSIT, END, END.plusDays(4))).isEqualTo(DEPOSIT);
		assertThat(calculator.calculate(1_000_000, 3_000_000, END, END.plusDays(365))).isEqualTo(3_000_000);
	}

	@Test
	@DisplayName("보증금 0원이면 연체해도 0원이다(policies.md 무료 대여: 징수하지 않고 신뢰 점수에만 반영)")
	void zeroDeposit() {
		assertThat(calculator.calculate(PRICE_PER_DAY, 0, END, END.plusDays(3))).isZero();
		assertThat(calculator.calculate(0, 0, END, END.plusDays(3))).isZero();
	}

	@Test
	@DisplayName("상세 표시: OVERDUE와 예정일이 지난 RENTING은 오늘 기준, 그 외는 확정값이다")
	void displayFee() {
		LocalDate today = END.plusDays(2);
		assertThat(calculator.displayFee(rental(RentalStatus.OVERDUE, 0), today)).isEqualTo(30_000);
		// 연체 배치 전(00:00~00:05)
		assertThat(calculator.displayFee(rental(RentalStatus.RENTING, 0), today)).isEqualTo(30_000);
		assertThat(calculator.displayFee(rental(RentalStatus.RENTING, 0), END)).isZero();
		assertThat(calculator.displayFee(rental(RentalStatus.RETURNED, 15_000), today)).isEqualTo(15_000);
		assertThat(calculator.displayFee(rental(RentalStatus.COMPLETED, 15_000), today)).isEqualTo(15_000);
		assertThat(calculator.displayFee(rental(RentalStatus.RESERVED, 0), today)).isZero();
	}

	private static Rental rental(RentalStatus status, int overdueFee) {
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		ReflectionTestUtils.setField(rental, "status", status);
		ReflectionTestUtils.setField(rental, "overdueFee", overdueFee);
		return rental;
	}
}
