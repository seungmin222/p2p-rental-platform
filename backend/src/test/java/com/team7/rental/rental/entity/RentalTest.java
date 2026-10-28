package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

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

	@Test
	@DisplayName("대여 일수는 시작일·종료일을 모두 포함한다")
	void countDays() {
		assertThat(Rental.countDays(START, START)).isEqualTo(1);
		assertThat(Rental.countDays(START, END)).isEqualTo(3);
		assertThat(Rental.countDays(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2))).isEqualTo(4);
	}

	@Test
	@DisplayName("대여자·제공자는 당사자이고 역할과 상대가 구분된다")
	void participantRole() {
		// given: 제공자 2, 대여자 3
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.isParticipant(3L)).isTrue();
		assertThat(rental.isParticipant(2L)).isTrue();
		assertThat(rental.isParticipant(4L)).isFalse();
		assertThat(rental.roleOf(3L)).isEqualTo(RentalRole.RENTER);
		assertThat(rental.roleOf(2L)).isEqualTo(RentalRole.OWNER);
		assertThat(rental.counterpartOf(3L)).isEqualTo(2L);
		assertThat(rental.counterpartOf(2L)).isEqualTo(3L);
		assertThatThrownBy(() -> rental.roleOf(4L)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@EnumSource(value = CancelType.class, names = {"RENTER", "OWNER"})
	@DisplayName("결제 대기는 단독 취소되고 취소 시각·종류·사유·수수료가 기록된다")
	void cancelPendingPayment(CancelType type) {
		// given
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT);

		// when
		rental.cancel(3L, type, CancelReason.OTHER, "개인 사정", 0, NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(rental.getCanceledAt()).isEqualTo(NOW);
		assertThat(rental.getCanceledBy()).isEqualTo(3L);
		assertThat(rental.getCancelType()).isEqualTo(type);
		assertThat(rental.getCancelReason()).isEqualTo(CancelReason.OTHER);
		assertThat(rental.getCancelReasonText()).isEqualTo("개인 사정");
		assertThat(rental.getCancelFee()).isZero();
	}

	@Test
	@DisplayName("시작일이 지난 결제 대기도 단독 취소된다(자정 직전 생성)")
	void cancelPendingPaymentAfterStartDate() {
		// given: 시작일 전날 23:59에 생성, 결제 만료는 시작일 00:14
		LocalDateTime createdAt = START.minusDays(1).atTime(23, 59);
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT,
			createdAt.plusMinutes(15));
		LocalDateTime now = START.atTime(0, 5);

		// when
		rental.cancel(3L, CancelType.RENTER, CancelReason.SCHEDULE_CHANGE, null, 0, now);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(rental.getCanceledAt()).isEqualTo(now);
	}

	@ParameterizedTest
	@EnumSource(value = CancelType.class, names = {"RENTER", "OWNER"})
	@DisplayName("대여 예정은 시작일 전날까지 단독 취소되고 수수료가 기록된다")
	void cancelReservedBeforeStart(CancelType type) {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		LocalDateTime now = START.minusDays(1).atTime(23, 59);

		// when
		rental.cancel(2L, type, CancelReason.SCHEDULE_CHANGE, null, 9_000, now);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(rental.getCanceledAt()).isEqualTo(now);
		assertThat(rental.getCancelType()).isEqualTo(type);
		assertThat(rental.getCancelReasonText()).isNull();
		assertThat(rental.getCancelFee()).isEqualTo(9_000);
	}

	@ParameterizedTest
	@EnumSource(value = CancelType.class, names = {"RENTER", "OWNER"})
	@DisplayName("대여 예정을 시작일 당일·이후에 단독 취소하면 409 RENTAL_CANCEL_NOT_ALLOWED다")
	void cancelReservedOnOrAfterStart(CancelType type) {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertCancelFails(rental, type, START.atStartOfDay(), ErrorCode.RENTAL_CANCEL_NOT_ALLOWED);
		assertCancelFails(rental, type, START.plusDays(1).atTime(10, 0), ErrorCode.RENTAL_CANCEL_NOT_ALLOWED);
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("양측 동의 취소는 대여 예정이고 시작일 당일·이후면 된다")
	void cancelMutual() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		LocalDateTime now = START.atTime(10, 0);

		// when
		rental.cancel(3L, CancelType.MUTUAL, CancelReason.ITEM_PROBLEM, null, 0, now);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(rental.getCancelType()).isEqualTo(CancelType.MUTUAL);
		assertThat(rental.getCanceledAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("양측 동의 취소를 시작일 전이나 결제 대기에 하면 409 RENTAL_INVALID_TRANSITION이다")
	void cancelMutualNotAllowed() {
		// given
		Rental reserved = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		Rental pending = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, EXPIRES_AT);

		// then
		assertCancelFails(reserved, CancelType.MUTUAL, START.minusDays(1).atTime(10, 0),
			ErrorCode.RENTAL_INVALID_TRANSITION);
		assertCancelFails(pending, CancelType.MUTUAL, START.atTime(10, 0), ErrorCode.RENTAL_INVALID_TRANSITION);
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RENTING", "OVERDUE", "RETURNED", "COMPLETED", "CANCELED"})
	@DisplayName("결제 대기·대여 예정이 아니면 어떤 취소든 409 RENTAL_INVALID_TRANSITION이다")
	void cancelInvalidStatus(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		for (CancelType type : List.of(CancelType.RENTER, CancelType.OWNER, CancelType.MUTUAL)) {
			assertCancelFails(rental, type, NOW, ErrorCode.RENTAL_INVALID_TRANSITION);
			assertCancelFails(rental, type, START.atTime(10, 0), ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		assertThat(rental.getStatus()).isEqualTo(status);
		assertThat(rental.getCanceledAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = CancelType.class, names = {"PAYMENT_EXPIRED", "AMOUNT_MISMATCH", "NO_HANDOVER"})
	@DisplayName("시스템 취소 종류는 아직 지원하지 않는다")
	void cancelSystemTypeNotSupported(CancelType type) {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThatThrownBy(() -> rental.cancel(null, type, CancelReason.OTHER, "시스템", 0, NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private void assertCancelFails(Rental rental, CancelType type, LocalDateTime now, ErrorCode errorCode) {
		assertThatThrownBy(() -> rental.cancel(3L, type, CancelReason.SCHEDULE_CHANGE, null, 0, now))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(errorCode);
	}

	// 대여 예정에서 갈 수 없는 상태(RENTING 등)는 해당 전이 메서드(REQ-017·018)가 생기기 전까지 직접 넣는다
	private Rental withStatus(RentalStatus status) {
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		ReflectionTestUtils.setField(rental, "status", status);
		return rental;
	}
}
