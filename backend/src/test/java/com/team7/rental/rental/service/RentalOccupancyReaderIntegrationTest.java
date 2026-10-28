package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 겹침 판정을 실제 JPQL로 확인한다. 기존 대여 [11/10, 11/12] 기준 */
class RentalOccupancyReaderIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 10);
	private static final LocalDate END = LocalDate.of(2026, 11, 12);

	@Autowired
	private RentalOccupancyReader rentalOccupancyReader;

	private Item item;
	private Member renter;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@ParameterizedTest(name = "[{0}, {1}] → {2}")
	@CsvSource({
		"2026-11-12, 2026-11-14, true", // 기존 종료일 == 새 시작일
		"2026-11-08, 2026-11-10, true", // 새 종료일 == 기존 시작일
		"2026-11-11, 2026-11-11, true", // 기존 안에 포함
		"2026-11-09, 2026-11-13, true", // 기존을 감쌈
		"2026-11-11, 2026-11-20, true", // 뒤로 걸침
		"2026-11-05, 2026-11-09, false", // 하루 차이로 앞에서 분리
		"2026-11-13, 2026-11-15, false", // 하루 차이로 뒤에서 분리
		"2026-11-01, 2026-11-03, false" // 완전히 분리
	})
	@DisplayName("양 끝을 포함해 겹침을 판정한다")
	void overlapBoundary(LocalDate from, LocalDate to, boolean expected) {
		// given
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);

		// when, then
		assertThat(rentalOccupancyReader.isOccupied(item.getId(), from, to, NOW)).isEqualTo(expected);
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RESERVED", "RENTING", "OVERDUE"})
	@DisplayName("RESERVED·RENTING·OVERDUE는 기간을 점유한다")
	void occupyingStatuses(RentalStatus status) {
		// given
		rentalFixture.createRental(item, renter, status, START, END);

		// when, then
		assertThat(rentalOccupancyReader.findOccupied(item.getId(), START, END, NOW))
			.containsExactly(new OccupiedPeriod(START, END));
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RETURNED", "COMPLETED", "CANCELED"})
	@DisplayName("RETURNED·COMPLETED·CANCELED는 기간을 점유하지 않는다")
	void notOccupyingStatuses(RentalStatus status) {
		// given
		rentalFixture.createRental(item, renter, status, START, END);

		// when, then
		assertThat(rentalOccupancyReader.isOccupied(item.getId(), START, END, NOW)).isFalse();
	}

	@Test
	@DisplayName("PENDING_PAYMENT는 만료 시각 전까지만 점유하고, 만료 시각과 정확히 같으면 점유하지 않는다")
	void pendingPaymentExpiry() {
		// given
		LocalDateTime expiresAt = NOW.plusMinutes(15);
		rentalFixture.createPendingPayment(item, renter, START, END, expiresAt);

		// when, then
		assertThat(rentalOccupancyReader.isOccupied(item.getId(), START, END, expiresAt.minusSeconds(1))).isTrue();
		assertThat(rentalOccupancyReader.isOccupied(item.getId(), START, END, expiresAt)).isFalse();
	}

	@Test
	@DisplayName("다른 물품의 대여는 점유로 세지 않는다")
	void otherItem() {
		// given
		Item otherItem = fixture.createItem(fixture.createVerifiedMember(), START, END).item();
		rentalFixture.createRental(otherItem, renter, RentalStatus.RESERVED, START, END);

		// when, then
		assertThat(rentalOccupancyReader.isOccupied(item.getId(), START, END, NOW)).isFalse();
	}
}
