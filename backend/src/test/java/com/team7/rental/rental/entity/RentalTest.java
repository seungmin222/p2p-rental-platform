package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
	@DisplayName("시스템 취소 종류는 cancel로 받지 않는다(전용 전이 메서드를 쓴다)")
	void cancelSystemTypeNotSupported(CancelType type) {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThatThrownBy(() -> rental.cancel(null, type, CancelReason.OTHER, "시스템", 0, NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("결제 승인은 PENDING_PAYMENT → RESERVED, 다른 상태면 409다")
	void confirmPayment() {
		// given
		Rental pending = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, NOW);
		Rental reserved = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// when
		pending.confirmPayment();

		// then
		assertThat(pending.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThatThrownBy(reserved::confirmPayment).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("결제 만료·금액 불일치는 시스템 취소(취소자 없음, 수수료 0)이고 PENDING_PAYMENT에서만 된다")
	void systemCancelsFromPending() {
		// given
		Rental expired = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, NOW);
		Rental mismatch = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, NOW);
		Rental reserved = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// when
		expired.expirePayment(NOW);
		mismatch.cancelForAmountMismatch(NOW);

		// then
		assertThat(expired.getCancelType()).isEqualTo(CancelType.PAYMENT_EXPIRED);
		assertThat(expired.getCanceledAt()).isEqualTo(NOW);
		assertThat(expired.getCanceledBy()).isNull();
		assertThat(expired.getCancelFee()).isZero();
		assertThat(mismatch.getCancelType()).isEqualTo(CancelType.AMOUNT_MISMATCH);
		assertThatThrownBy(() -> reserved.expirePayment(NOW)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> reserved.cancelForAmountMismatch(NOW)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("결제 만료 판정: 만료 시각과 같으면 만료, 1초 전이면 아니다")
	void paymentExpiredBoundary() {
		// given
		Rental rental = Rental.createPendingPayment(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT, NOW);

		// then
		assertThat(rental.isPaymentExpired(NOW)).isTrue();
		assertThat(rental.isPaymentExpired(NOW.minusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("인계 없음 자동 취소: 오늘 > 시작일 + 3일이고 RESERVED일 때만 대상이고 CANCELED(NO_HANDOVER)가 된다")
	void noHandoverCancel() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertThat(rental.isNoHandoverCancelDue(START.plusDays(3), 3)).isFalse();
		assertThat(rental.isNoHandoverCancelDue(START.plusDays(4), 3)).isTrue();

		// when
		rental.cancelForNoHandover(NOW);

		// then
		assertThat(rental.getCancelType()).isEqualTo(CancelType.NO_HANDOVER);
		assertThat(rental.isNoHandoverCancelDue(START.plusDays(4), 3)).isFalse();
		assertThatThrownBy(() -> rental.cancelForNoHandover(NOW)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("시작일 당일에 인계하면 RENTING이 되고 인계 시각이 기록된다")
	void handoverOnStartDate() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		LocalDateTime now = START.atStartOfDay();

		// when
		rental.handover(now);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RENTING);
		assertThat(rental.getHandedOverAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("시작일 이후에도 인계할 수 있다")
	void handoverAfterStartDate() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// when
		rental.handover(END.atTime(23, 59));

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RENTING);
	}

	@Test
	@DisplayName("시작일 전에 인계하면 409 RENTAL_HANDOVER_TOO_EARLY이고 상태가 그대로다")
	void handoverTooEarly() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);

		// then
		assertTransitionFails(() -> rental.handover(START.minusDays(1).atTime(23, 59, 59)),
			ErrorCode.RENTAL_HANDOVER_TOO_EARLY);
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(rental.getHandedOverAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"PENDING_PAYMENT", "RENTING", "OVERDUE", "RETURNED", "COMPLETED",
		"CANCELED"})
	@DisplayName("대여 예정이 아니면 인계는 409 RENTAL_INVALID_TRANSITION이다(시작일 검사보다 먼저)")
	void handoverInvalidStatus(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		assertTransitionFails(() -> rental.handover(START.atTime(10, 0)), ErrorCode.RENTAL_INVALID_TRANSITION);
		assertTransitionFails(() -> rental.handover(NOW), ErrorCode.RENTAL_INVALID_TRANSITION);
		assertThat(rental.getStatus()).isEqualTo(status);
		assertThat(rental.getHandedOverAt()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RENTING", "OVERDUE"})
	@DisplayName("대여 중·연체에서 반납 요청하면 RETURNED가 되고 반납 시각이 기록되며 연체료는 그대로다")
	void requestReturn(RentalStatus status) {
		// given
		Rental rental = withStatus(status);
		LocalDateTime now = END.atTime(18, 0);

		// when
		rental.requestReturn(now);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RETURNED);
		assertThat(rental.getReturnedAt()).isEqualTo(now);
		assertThat(rental.getOverdueFee()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"PENDING_PAYMENT", "RESERVED", "RETURNED", "COMPLETED",
		"CANCELED"})
	@DisplayName("대여 중·연체가 아니면 반납 요청은 409 RENTAL_INVALID_TRANSITION이다")
	void requestReturnInvalidStatus(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		assertTransitionFails(() -> rental.requestReturn(NOW), ErrorCode.RENTAL_INVALID_TRANSITION);
		assertThat(rental.getStatus()).isEqualTo(status);
		assertThat(rental.getReturnedAt()).isNull();
	}

	@Test
	@DisplayName("반납 완료에서 '정상'이면 COMPLETED가 되고 완료 시각이 기록된다")
	void confirmNormal() {
		// given
		Rental rental = withStatus(RentalStatus.RETURNED);

		// when
		rental.confirmNormal(NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(rental.getCompletedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("'이상' 표시는 RETURNED를 유지하고, 1순위는 이어서 완료하면 COMPLETED다")
	void markAbnormalThenComplete() {
		// given
		Rental rental = withStatus(RentalStatus.RETURNED);

		// when
		rental.markAbnormal();

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RETURNED);
		assertThat(rental.getCompletedAt()).isNull();

		// when
		rental.complete(NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(rental.getCompletedAt()).isEqualTo(NOW);
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"PENDING_PAYMENT", "RESERVED", "RENTING", "OVERDUE", "COMPLETED",
		"CANCELED"})
	@DisplayName("반납 완료가 아니면 정상·이상·완료는 409 RENTAL_INVALID_TRANSITION이다")
	void confirmInvalidStatus(RentalStatus status) {
		// given
		Rental rental = withStatus(status);

		// then
		assertTransitionFails(() -> rental.confirmNormal(NOW), ErrorCode.RENTAL_INVALID_TRANSITION);
		assertTransitionFails(rental::markAbnormal, ErrorCode.RENTAL_INVALID_TRANSITION);
		assertTransitionFails(() -> rental.complete(NOW), ErrorCode.RENTAL_INVALID_TRANSITION);
		assertThat(rental.getStatus()).isEqualTo(status);
		assertThat(rental.getCompletedAt()).isNull();
	}

	@Test
	@DisplayName("전이 메서드만으로 예약 → 인계 → 반납 → 완료까지 간다")
	void fullLifecycle() {
		// given
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		LocalDateTime handedOverAt = START.atTime(9, 0);
		LocalDateTime returnedAt = END.atTime(18, 0);
		LocalDateTime completedAt = END.plusDays(1).atTime(10, 0);

		// when
		rental.handover(handedOverAt);
		rental.requestReturn(returnedAt);
		rental.confirmNormal(completedAt);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(rental.getHandedOverAt()).isEqualTo(handedOverAt);
		assertThat(rental.getReturnedAt()).isEqualTo(returnedAt);
		assertThat(rental.getCompletedAt()).isEqualTo(completedAt);
		assertThat(rental.getCanceledAt()).isNull();
	}

	@Test
	@DisplayName("자동 상태 확인은 반납 시각 + N일 정각부터 대상이고 1초 전은 아니다")
	void autoConfirmDueBoundary() {
		// given
		Rental rental = withStatus(RentalStatus.RENTING);
		LocalDateTime returnedAt = END.atTime(18, 0);
		rental.requestReturn(returnedAt);

		// then
		assertThat(rental.isAutoConfirmDue(returnedAt.plusDays(3).minusSeconds(1), 3)).isFalse();
		assertThat(rental.isAutoConfirmDue(returnedAt.plusDays(3), 3)).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RENTING", "COMPLETED"})
	@DisplayName("반납 완료가 아니면 자동 상태 확인 대상이 아니다")
	void autoConfirmDueOnlyReturned(RentalStatus status) {
		// given
		Rental rental = withStatus(status);
		ReflectionTestUtils.setField(rental, "returnedAt", NOW);

		// then
		assertThat(rental.isAutoConfirmDue(NOW.plusDays(10), 3)).isFalse();
	}

	@Test
	@DisplayName("후기 작성 기간: COMPLETED이고 completedAt + 기한 정각까지 true, 1초 지나면 false")
	void canWriteReviewBoundary() {
		assertThat(Rental.canWriteReview(RentalStatus.COMPLETED, NOW, NOW, 7)).isTrue();
		assertThat(Rental.canWriteReview(RentalStatus.COMPLETED, NOW, NOW.plusDays(7), 7)).isTrue();
		assertThat(Rental.canWriteReview(RentalStatus.COMPLETED, NOW, NOW.plusDays(7).plusSeconds(1), 7)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("COMPLETED가 아니면 completedAt이 있어도 후기 작성 기간이 아니다")
	void canWriteReviewOnlyCompleted(RentalStatus status) {
		assertThat(Rental.canWriteReview(status, NOW, NOW, 7)).isFalse();
	}

	@Test
	@DisplayName("COMPLETED인데 completedAt이 없는 깨진 행은 후기 작성 기간이 아니다")
	void canWriteReviewWithoutCompletedAt() {
		assertThat(Rental.canWriteReview(RentalStatus.COMPLETED, null, NOW, 7)).isFalse();
	}

	@Test
	@DisplayName("엔티티 메서드는 자기 상태·completedAt으로 같은 판정을 한다")
	void canWriteReviewInstance() {
		// given: 인계 → 반납 요청 → 정상 확인(완료) 경로로 COMPLETED
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		rental.handover(START.atTime(10, 0));
		rental.requestReturn(END.atTime(18, 0));
		LocalDateTime completedAt = END.atTime(20, 0);
		rental.confirmNormal(completedAt);

		// then
		assertThat(rental.canWriteReview(completedAt.plusDays(7), 7)).isTrue();
		assertThat(rental.canWriteReview(completedAt.plusDays(7).plusSeconds(1), 7)).isFalse();
		assertThat(withStatus(RentalStatus.RENTING).canWriteReview(NOW, 7)).isFalse();
	}

	private void assertTransitionFails(ThrowingCallable transition, ErrorCode errorCode) {
		assertThatThrownBy(transition)
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(errorCode);
	}

	private void assertCancelFails(Rental rental, CancelType type, LocalDateTime now, ErrorCode errorCode) {
		assertThatThrownBy(() -> rental.cancel(3L, type, CancelReason.SCHEDULE_CHANGE, null, 0, now))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(errorCode);
	}

	// 각 상태의 전이 가드만 보려고 상태를 직접 넣는다(전이 경로 전체는 fullLifecycle)
	private Rental withStatus(RentalStatus status) {
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, PRICE_PER_DAY, DEPOSIT);
		ReflectionTestUtils.setField(rental, "status", status);
		return rental;
	}
}
