package com.team7.rental.rental.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
import com.team7.rental.rental.service.RentalCancelService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 양측 동의 취소 요청 만료 배치 (REQ-015) */
class CancelAgreementExpireTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDateTime NOW = START.atTime(12, 0);

	@Autowired
	private RentalCancelService rentalCancelService;

	@Autowired
	private CancelAgreementExpireJob cancelAgreementExpireJob;

	@Autowired
	private RentalCancelAgreementRepository agreementRepository;

	private Member renter;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		rental = rentalFixture.createRental(
			fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item(), renter,
			RentalStatus.RESERVED, START, START.plusDays(2));
	}

	@Test
	@DisplayName("expires_at == now는 만료하고 1초 뒤는 유지하며, 두 번 실행해도 결과가 같다")
	void expireBoundaryAndIdempotent() {
		// given
		Long atNow = save(NOW).getId();
		Long oneSecondLater = save(NOW.plusSeconds(1)).getId();

		// when
		int first = rentalCancelService.expireOverdue(NOW);
		int second = rentalCancelService.expireOverdue(NOW);

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		RentalCancelAgreement expired = agreementRepository.findById(atNow).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(CancelAgreementStatus.EXPIRED);
		assertThat(expired.getRespondedAt()).isNull();
		assertThat(expired.getUpdatedAt()).isEqualTo(NOW);
		assertThat(statusOf(oneSecondLater)).isEqualTo(CancelAgreementStatus.REQUESTED);
	}

	@Test
	@DisplayName("이미 수락·거절한 요청은 만료 시각이 지났어도 바꾸지 않는다")
	void keepRespondedAgreements() {
		// given
		RentalCancelAgreement accepted = save(NOW.minusHours(1));
		RentalCancelAgreement rejected = save(NOW.minusHours(1));
		accepted.accept(NOW.minusHours(2));
		rejected.reject(NOW.minusHours(2));
		agreementRepository.save(accepted);
		agreementRepository.save(rejected);

		// when
		int expired = rentalCancelService.expireOverdue(NOW);

		// then
		assertThat(expired).isZero();
		assertThat(statusOf(accepted.getId())).isEqualTo(CancelAgreementStatus.ACCEPTED);
		assertThat(statusOf(rejected.getId())).isEqualTo(CancelAgreementStatus.REJECTED);
	}

	@Test
	@DisplayName("배치 잡은 Clock의 현재 시각으로 만료한다")
	void jobUsesClock() {
		// given
		Long overdue = save(NOW.minusMinutes(1)).getId();

		// when
		cancelAgreementExpireJob.run();

		// then
		assertThat(statusOf(overdue)).isEqualTo(CancelAgreementStatus.EXPIRED);
	}

	private RentalCancelAgreement save(LocalDateTime expiresAt) {
		return agreementRepository.save(RentalCancelAgreement.request(rental.getId(), renter.getId(),
			CancelReason.SCHEDULE_CHANGE, null, expiresAt));
	}

	private CancelAgreementStatus statusOf(Long agreementId) {
		return agreementRepository.findById(agreementId).orElseThrow().getStatus();
	}
}
