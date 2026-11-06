package com.team7.rental.rental.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 반납 리마인더 배치 (REQ-031, policies.md return.reminder). 오늘 = 2026-11-20 09:00, 대상은 RENTING이고 endDate = 내일 */
class ReturnReminderJobTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);
	private static final LocalDate TOMORROW = NOW.toLocalDate().plusDays(1);

	@Autowired
	private ReturnReminderJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 30)).item();
	}

	@Test
	@DisplayName("내일이 예정일인 RENTING만, 대여자에게만 RETURN_REMINDER를 보내고 상태는 바꾸지 않는다")
	void remindsRenterOnly() {
		// given: 대상 1건 + 모레·오늘 예정 RENTING, 내일 예정 RESERVED·OVERDUE
		Rental target = renting(TOMORROW.minusDays(2), TOMORROW);
		renting(TOMORROW.plusDays(1), TOMORROW.plusDays(1));
		renting(TOMORROW.minusDays(5), TOMORROW.minusDays(1));
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, TOMORROW, TOMORROW);
		rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, TOMORROW.minusDays(3), TOMORROW);

		// when
		int sent = job.remindDue(NOW);

		// then
		assertThat(sent).isEqualTo(1);
		assertThat(notificationPort.sent()).containsExactly(new Sent(renter.getId(), Type.RETURN_REMINDER,
			TargetType.RENTAL, target.getId(), "RETURN_REMINDER:" + target.getId() + ":2026-11-21"));
		assertThat(rentalRepository.findById(target.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RENTING);
		assertThat(historyRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("두 번 돌리면 같은 event_id로 다시 발행한다(중복 저장은 알림 구현이 event_id 유니크로 막는다)")
	void rerunSendsSameEventId() {
		// given
		Rental target = renting(TOMORROW.minusDays(2), TOMORROW);

		// when
		job.remindDue(NOW);
		job.remindDue(NOW);

		// then
		assertThat(notificationPort.sent()).hasSize(2)
			.extracting(Sent::eventId).containsOnly("RETURN_REMINDER:" + target.getId() + ":2026-11-21");
	}

	private Rental renting(LocalDate start, LocalDate end) {
		return rentalFixture.createRental(item, renter, RentalStatus.RENTING, start, end);
	}
}
