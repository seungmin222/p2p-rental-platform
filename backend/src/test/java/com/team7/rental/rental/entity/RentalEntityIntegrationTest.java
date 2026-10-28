package com.team7.rental.rental.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

class RentalEntityIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository rentalStatusHistoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("결제 대기 대여를 저장하고 다시 읽으면 값이 같고 상태는 문자열로 저장된다")
	void saveAndFindRental() {
		// given
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		Rental saved = rentalRepository.save(Rental.createPendingPayment(item.getId(), item.getOwnerId(),
			renter.getId(), START, END, item.getPricePerDay(), item.getDeposit(), NOW.plusMinutes(15)));

		// when
		Rental found = rentalRepository.findById(saved.getId()).orElseThrow();
		String storedStatus = jdbcTemplate.queryForObject("SELECT status FROM rentals WHERE id = ?", String.class,
			saved.getId());

		// then
		assertThat(found.getItemId()).isEqualTo(item.getId());
		assertThat(found.getOwnerId()).isEqualTo(owner.member().getId());
		assertThat(found.getRenterId()).isEqualTo(renter.getId());
		assertThat(found.getStartDate()).isEqualTo(START);
		assertThat(found.getEndDate()).isEqualTo(END);
		assertThat(found.getPricePerDay()).isEqualTo(MemberItemFixture.PRICE_PER_DAY);
		assertThat(found.getDeposit()).isEqualTo(MemberItemFixture.DEPOSIT);
		assertThat(found.getRentalFee()).isEqualTo(MemberItemFixture.PRICE_PER_DAY * 3);
		assertThat(found.getStatus()).isEqualTo(RentalStatus.PENDING_PAYMENT);
		assertThat(found.getPaymentExpiresAt()).isEqualTo(NOW.plusMinutes(15));
		assertThat(found.getCancelFee()).isZero();
		assertThat(found.getOverdueFee()).isZero();
		assertThat(found.getExtensionCount()).isZero();
		assertThat(found.getCancelType()).isNull();
		assertThat(found.getCanceledBy()).isNull();
		assertThat(found.getVersion()).isZero();
		assertThat(found.getCreatedAt()).isEqualTo(NOW);
		assertThat(storedStatus).isEqualTo("PENDING_PAYMENT");
	}

	@Test
	@DisplayName("상태 이력을 저장하고 다시 읽으면 값이 같고 상태는 문자열로 저장된다")
	void saveAndFindHistory() {
		// given
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
		RentalStatusHistory created = rentalStatusHistoryRepository.save(
			RentalStatusHistory.create(rental.getId(), null, RentalStatus.RESERVED, renter.getId(), null, NOW));
		RentalStatusHistory canceled = rentalStatusHistoryRepository.save(
			RentalStatusHistory.create(rental.getId(), RentalStatus.RESERVED, RentalStatus.CANCELED, null,
				"rental.autoCancelNoHandover", NOW.plusDays(1)));

		// when
		RentalStatusHistory foundCreated = rentalStatusHistoryRepository.findById(created.getId()).orElseThrow();
		RentalStatusHistory foundCanceled = rentalStatusHistoryRepository.findById(canceled.getId()).orElseThrow();
		String storedToStatus = jdbcTemplate.queryForObject(
			"SELECT to_status FROM rental_status_histories WHERE id = ?", String.class, canceled.getId());

		// then
		assertThat(foundCreated.getRentalId()).isEqualTo(rental.getId());
		assertThat(foundCreated.getFromStatus()).isNull();
		assertThat(foundCreated.getToStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(foundCreated.getChangedBy()).isEqualTo(renter.getId());
		assertThat(foundCreated.getReason()).isNull();
		assertThat(foundCreated.getChangedAt()).isEqualTo(NOW);
		assertThat(foundCanceled.getFromStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(foundCanceled.getToStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(foundCanceled.getChangedBy()).isNull();
		assertThat(foundCanceled.getReason()).isEqualTo("rental.autoCancelNoHandover");
		assertThat(foundCanceled.getChangedAt()).isEqualTo(NOW.plusDays(1));
		assertThat(storedToStatus).isEqualTo("CANCELED");
	}

	@Test
	@DisplayName("픽스처는 지정한 상태·기간의 대여를 저장한다")
	void fixtureCreatesRentalWithStatus() {
		// given
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();

		// when
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);

		// then
		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(RentalStatus.RENTING);
		assertThat(found.getStartDate()).isEqualTo(START);
		assertThat(found.getEndDate()).isEqualTo(END);
	}

	@Test
	@DisplayName("없는 물품을 참조하는 대여는 저장되지 않는다")
	void itemForeignKey() {
		// given
		Member owner = fixture.createMember();
		Member renter = fixture.createMember();

		// then
		assertThatThrownBy(() -> rentalRepository.save(
			Rental.createReserved(Long.MAX_VALUE, owner.getId(), renter.getId(), START, END, 10_000, 0)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
