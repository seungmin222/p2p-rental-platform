package com.team7.rental.payment.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementAmount;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 정산 지급 배치 (REQ-039): SCHEDULED이고 지급 예정일 ≤ 오늘이면 PAID, 다시 돌아도 같다 */
class SettlementPayoutJobTest extends IntegrationTestSupport {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
	private static final LocalDateTime RUN_AT = TODAY.atTime(0, 30);

	@Autowired
	private SettlementPayoutJob job;

	@Autowired
	private SettlementRepository settlementRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private int nextStart;

	@BeforeEach
	void setUp() {
		testClock.fixAt(RUN_AT);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
	}

	@Test
	@DisplayName("예정일이 지났거나 오늘인 SCHEDULED만 PAID로 바꾸고, 미래·HELD·PAID는 그대로다")
	void paysDueOnly() {
		Settlement past = scheduled(TODAY.minusDays(3));
		Settlement today = scheduled(TODAY);
		Settlement future = scheduled(TODAY.plusDays(1));
		Settlement held = scheduled(TODAY.minusDays(1));
		ReflectionTestUtils.setField(held, "status", SettlementStatus.HELD);
		settlementRepository.save(held);

		assertThat(job.payDue(RUN_AT)).isEqualTo(2);

		assertThat(reload(past).getStatus()).isEqualTo(SettlementStatus.PAID);
		assertThat(reload(past).getPaidAt()).isEqualTo(RUN_AT);
		assertThat(reload(today).getStatus()).isEqualTo(SettlementStatus.PAID);
		assertThat(reload(future).getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
		assertThat(reload(held).getStatus()).isEqualTo(SettlementStatus.HELD);
	}

	@Test
	@DisplayName("다시 돌려도 이미 지급한 건은 건드리지 않는다(지급 시각 유지)")
	void idempotent() {
		Settlement due = scheduled(TODAY);
		job.payDue(RUN_AT);

		LocalDateTime rerunAt = RUN_AT.plusHours(1);
		testClock.fixAt(rerunAt);
		assertThat(job.payDue(rerunAt)).isZero();

		assertThat(reload(due).getStatus()).isEqualTo(SettlementStatus.PAID);
		assertThat(reload(due).getPaidAt()).isEqualTo(RUN_AT);
	}

	@Test
	@DisplayName("다음 날 회차는 그날 예정일이 된 건을 지급한다")
	void nextDay() {
		Settlement tomorrow = scheduled(TODAY.plusDays(1));
		assertThat(job.payDue(RUN_AT)).isZero();

		LocalDateTime nextRun = RUN_AT.plusDays(1);
		testClock.fixAt(nextRun);
		assertThat(job.payDue(nextRun)).isEqualTo(1);
		assertThat(reload(tomorrow).getStatus()).isEqualTo(SettlementStatus.PAID);
	}

	private Settlement scheduled(LocalDate scheduledDate) {
		LocalDate start = LocalDate.of(2026, 10, 1).plusDays(nextStart);
		nextStart += 2;
		Long rentalId = rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, start, start).getId();
		return settlementRepository.save(Settlement.schedule(rentalId, owner.getId(), SettlementSource.COMPLETED,
			SettlementAmount.ofCompletion(10_000, 0, 0, 0, 10), scheduledDate));
	}

	private Settlement reload(Settlement settlement) {
		return settlementRepository.findById(settlement.getId()).orElseThrow();
	}
}
