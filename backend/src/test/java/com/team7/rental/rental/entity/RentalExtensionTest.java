package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

/** 연장 전이 (REQ-038, state-machines.md RENTING → RENTING, ExtensionStatus) */
class RentalExtensionTest {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDate NEW_END = LocalDate.of(2026, 11, 8);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 20, 0);
	private static final int MAX_COUNT = 2;

	private Rental rentalIn(RentalStatus status) {
		Rental rental = Rental.createReserved(1L, 2L, 3L, START, END, 10_000, 50_000);
		ReflectionTestUtils.setField(rental, "status", status);
		return rental;
	}

	@Test
	@DisplayName("RENTING만 연장을 요청할 수 있다: OVERDUE는 RENTAL_EXTENSION_OVERDUE, 그 외는 RENTAL_INVALID_TRANSITION")
	void extendableOnlyWhenRenting() {
		rentalIn(RentalStatus.RENTING).validateExtendable(MAX_COUNT);

		assertThatThrownBy(() -> rentalIn(RentalStatus.OVERDUE).validateExtendable(MAX_COUNT))
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_EXTENSION_OVERDUE);
		for (RentalStatus status : new RentalStatus[] {RentalStatus.PENDING_PAYMENT, RentalStatus.RESERVED,
			RentalStatus.RETURNED, RentalStatus.COMPLETED, RentalStatus.CANCELED}) {
			assertThatThrownBy(() -> rentalIn(status).validateExtendable(MAX_COUNT))
				.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	@Test
	@DisplayName("연장하면 종료일이 바뀌고 횟수가 늘며, 횟수가 한도에 닿으면 더 요청할 수 없다")
	void extendUntilLimit() {
		// given
		Rental rental = rentalIn(RentalStatus.RENTING);

		// when
		rental.extend(END, NEW_END, MAX_COUNT);
		rental.extend(NEW_END, NEW_END.plusDays(2), MAX_COUNT);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RENTING);
		assertThat(rental.getEndDate()).isEqualTo(NEW_END.plusDays(2));
		assertThat(rental.getExtensionCount()).isEqualTo(2);
		assertThatThrownBy(() -> rental.validateExtendable(MAX_COUNT))
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_EXTENSION_LIMIT_EXCEEDED);
		assertThatThrownBy(() -> rental.extend(NEW_END.plusDays(2), NEW_END.plusDays(3), MAX_COUNT))
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_EXTENSION_LIMIT_EXCEEDED);
	}

	@Test
	@DisplayName("RENTING이 아니거나 종료일이 요청 때와 다르면 확정할 수 없다")
	void extendGuards() {
		Rental overdue = rentalIn(RentalStatus.OVERDUE);
		Rental renting = rentalIn(RentalStatus.RENTING);

		assertThat(overdue.canApplyExtension(END)).isFalse();
		assertThat(renting.canApplyExtension(END.minusDays(1))).isFalse();
		assertThatThrownBy(() -> overdue.extend(END, NEW_END, MAX_COUNT)).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
		assertThatThrownBy(() -> renting.extend(END.minusDays(1), NEW_END, MAX_COUNT))
			.extracting("errorCode").isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("결제 대기 연장은 만료 시각 전까지만 대기이고(같은 시각은 만료), 확정·취소는 대기에서만 된다")
	void extensionStatus() {
		// given
		RentalExtension pending = RentalExtension.pendingPayment(1L, END, NEW_END, 30_000, NOW.plusMinutes(15));

		// then
		assertThat(pending.firstDate()).isEqualTo(END.plusDays(1));
		assertThat(pending.days()).isEqualTo(3);
		assertThat(pending.isPendingAt(NOW.plusMinutes(14))).isTrue();
		assertThat(pending.isPendingAt(NOW.plusMinutes(15))).isFalse();
		assertThat(pending.isExpiredAt(NOW.plusMinutes(15))).isTrue();

		// when
		pending.confirm(10L);

		// then
		assertThat(pending.getStatus()).isEqualTo(ExtensionStatus.CONFIRMED);
		assertThat(pending.getPaymentId()).isEqualTo(10L);
		assertThatThrownBy(pending::cancel).extracting("errorCode").isEqualTo(ErrorCode.RENTAL_INVALID_TRANSITION);
	}

	@Test
	@DisplayName("결제 없이 확정한 연장은 결제 ID가 없고, 연장 후 종료일은 기존 종료일보다 뒤여야 한다")
	void confirmedWithoutPayment() {
		RentalExtension extension = RentalExtension.confirmedWithoutPayment(1L, END, NEW_END, 0, NOW);

		assertThat(extension.getStatus()).isEqualTo(ExtensionStatus.CONFIRMED);
		assertThat(extension.getPaymentId()).isNull();
		assertThatThrownBy(() -> RentalExtension.pendingPayment(1L, END, END, 0, NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
