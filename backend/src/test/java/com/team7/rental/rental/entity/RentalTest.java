package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class RentalTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int PRICE_PER_DAY = 10_000;
	private static final int DEPOSIT = 50_000;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDateTime EXPIRES_AT = NOW.plusMinutes(15);

	@Test
	@DisplayName("결제 대기로 생성하면 PENDING_PAYMENT이고 만료 시각과 스냅샷 값을 가진다")
	void createPendingPayment() {
		// when
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.PENDING_PAYMENT);
		assertThat(rental.getPaymentExpiresAt()).isEqualTo(EXPIRES_AT);
		assertSnapshotAndDefaults(rental);
	}

	@Test
	@DisplayName("바로 확정으로 생성하면 RESERVED이고 결제 만료 시각이 없다")
	void createReserved() {
		// when
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(rental.getPaymentExpiresAt()).isNull();
		assertSnapshotAndDefaults(rental);
	}

	private void assertSnapshotAndDefaults(Rental rental) {
		assertThat(rental.getItemId()).isEqualTo(1L);
		assertThat(rental.getOwnerId()).isEqualTo(2L);
		assertThat(rental.getRenterId()).isEqualTo(3L);
		assertThat(rental.getStartDate()).isEqualTo(START);
		assertThat(rental.getEndDate()).isEqualTo(END);
		assertThat(rental.getPricePerDay()).isEqualTo(PRICE_PER_DAY);
		assertThat(rental.getDeposit()).isEqualTo(DEPOSIT);
		assertThat(rental.getRentalFee()).isEqualTo(30_000);
		assertThat(rental.getCancelFee()).isZero();
		assertThat(rental.getOverdueFee()).isZero();
		assertThat(rental.getExtensionCount()).isZero();
		assertThat(rental.getHandedOverAt()).isNull();
		assertThat(rental.getReturnedAt()).isNull();
		assertThat(rental.getCompletedAt()).isNull();
		assertThat(rental.getCanceledAt()).isNull();
		assertThat(rental.getCancelType()).isNull();
		assertThat(rental.getCancelReason()).isNull();
		assertThat(rental.getCancelReasonText()).isNull();
		assertThat(rental.getCanceledBy()).isNull();
	}

	@Test
	@DisplayName("시작일과 종료일이 같으면 1일치 대여료다")
	void rentalFeeOneDay() {
		// when
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, START, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.getRentalFee()).isEqualTo(10_000);
	}

	@Test
	@DisplayName("대여료는 시작일과 종료일을 모두 포함한 일수로 계산한다")
	void rentalFeeSeveralDays() {
		// when
		Rental rental = Rental.createReserved(1L, 2L, 3L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7),
			PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.getRentalFee()).isEqualTo(70_000);
	}

	@Test
	@DisplayName("월 경계를 넘는 기간도 일수를 정확히 센다")
	void rentalFeeAcrossMonth() {
		// when 10/30, 10/31, 11/1, 11/2
		Rental rental = Rental.createReserved(1L, 2L, 3L, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2),
			PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.getRentalFee()).isEqualTo(40_000);
	}

	@Test
	@DisplayName("무료 대여는 대여료가 0원이다")
	void rentalFeeFree() {
		// when
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, 0, 0);

		// then
		assertThat(rental.getRentalFee()).isZero();
	}

	@Test
	@DisplayName("결제 대기 생성에 만료 시각이 없으면 NPE가 난다")
	void createPendingPaymentWithoutExpiresAt() {
		// then
		assertThatThrownBy(() -> Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("종료일이 시작일보다 앞서면 두 팩토리 모두 예외가 난다")
	void reversedPeriod() {
		// then
		assertThatThrownBy(() -> Rental.createReserved(1L, 2L, 3L, END, START, PRICE_PER_DAY, DEPOSIT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
			() -> Rental.createPendingPayment(1L, 2L, 3L, END, START, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("시작일이나 종료일이 없으면 예외가 난다")
	void nullPeriod() {
		// then
		assertThatThrownBy(() -> Rental.createReserved(1L, 2L, 3L, null, END, PRICE_PER_DAY, DEPOSIT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Rental.createReserved(1L, 2L, 3L, START, null, PRICE_PER_DAY, DEPOSIT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
			() -> Rental.createPendingPayment(1L, 2L, 3L, null, END, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("결제 대기도 시작일과 종료일이 같으면 1일치 대여료다")
	void pendingPaymentOneDay() {
		// when
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, START, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT);

		// then
		assertThat(rental.getRentalFee()).isEqualTo(PRICE_PER_DAY);
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RESERVED", "RENTING", "OVERDUE"})
	@DisplayName("RESERVED·RENTING·OVERDUE는 기간을 점유한다")
	void occupyingStatuses(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		assertThat(rental.isOccupyingPeriod(NOW)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RETURNED", "COMPLETED", "CANCELED"})
	@DisplayName("RETURNED·COMPLETED·CANCELED는 기간을 점유하지 않는다")
	void notOccupyingStatuses(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		assertThat(rental.isOccupyingPeriod(NOW)).isFalse();
	}

	@Test
	@DisplayName("PENDING_PAYMENT는 만료 1초 전까지 점유하고, 정확히 만료 시각부터 점유하지 않는다")
	void pendingPaymentExpiryBoundary() {
		// given
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT);

		// then
		assertThat(rental.isOccupyingPeriod(EXPIRES_AT.minusSeconds(1))).isTrue();
		assertThat(rental.isOccupyingPeriod(EXPIRES_AT)).isFalse();
		assertThat(rental.isOccupyingPeriod(EXPIRES_AT.plusSeconds(1))).isFalse();
	}

	// 상태 전이 메서드는 각 REQ 브랜치에서 만든다. 그 전까지는 상태를 직접 넣는다
	private Rental withStatus(RentalStatus status) {
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		ReflectionTestUtils.setField(rental, "status", status);
		return rental;
	}
}
