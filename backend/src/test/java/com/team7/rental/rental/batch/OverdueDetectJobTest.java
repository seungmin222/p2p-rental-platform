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
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 연체 감지 배치 (REQ-023·031). 오늘 = 2026-11-20, 대상은 RENTING이고 endDate &lt; 오늘 */
class OverdueDetectJobTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 0, 5);
	private static final LocalDate TODAY = NOW.toLocalDate();

	@Autowired
	private OverdueDetectJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	@Autowired
	private RentalExtensionRepository extensionRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	private Member owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 30)).item();
	}

	@Test
	@DisplayName("예정일이 지난 RENTING은 OVERDUE·이력(시스템, OVERDUE_BATCH)이 되고 대여자·제공자에게 OVERDUE 알림을 보낸다")
	void marksOverdueAndNotifiesBoth() {
		// given: 어제가 예정일
		Rental rental = renting(TODAY.minusDays(3), TODAY.minusDays(1));

		// when
		int marked = job.markDue(NOW);

		// then
		assertThat(marked).isEqualTo(1);
		assertThat(statusOf(rental)).isEqualTo(RentalStatus.OVERDUE);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getRentalId()).isEqualTo(rental.getId());
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RENTING);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.OVERDUE);
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_OVERDUE_BATCH);
		});
		String eventId = "OVERDUE:" + rental.getId();
		assertThat(notificationPort.sent()).containsExactlyInAnyOrder(
			new Sent(renter.getId(), Type.OVERDUE, TargetType.RENTAL, rental.getId(), eventId),
			new Sent(owner.getId(), Type.OVERDUE, TargetType.RENTAL, rental.getId(), eventId));
	}

	@Test
	@DisplayName("같은 시각에 두 번, 한 시간 뒤 재검사까지 돌려도 전환·이력·알림은 첫 회차에만 일어난다")
	void idempotent() {
		// given
		Rental rental = renting(TODAY.minusDays(3), TODAY.minusDays(1));

		// when
		int first = job.markDue(NOW);
		int second = job.markDue(NOW);
		int recheck = job.markDue(NOW.plusHours(1));

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		assertThat(recheck).isZero();
		assertThat(statusOf(rental)).isEqualTo(RentalStatus.OVERDUE);
		assertThat(historyRepository.findAll()).hasSize(1);
		assertThat(notificationPort.sent()).hasSize(2)
			.extracting(Sent::eventId).containsOnly("OVERDUE:" + rental.getId());
		// 양측이 같은 event_id라도 회원마다 1건씩 저장된다 (UK member_id, event_id, open-issues #49)
		assertThat(notificationRepository.findAll()).extracting(Notification::getMemberId, Notification::getEventId)
			.containsExactlyInAnyOrder(tuple(renter.getId(), "OVERDUE:" + rental.getId()),
				tuple(owner.getId(), "OVERDUE:" + rental.getId()));
	}

	@Test
	@DisplayName("예정일 당일 RENTING, 예정일이 지났어도 RENTING이 아닌 대여는 그대로 둔다")
	void skipsNotDue() {
		// given
		Rental dueToday = renting(TODAY.minusDays(2), TODAY);
		Rental reserved = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, TODAY.minusDays(9),
			TODAY.minusDays(8));
		Rental returned = rentalFixture.createReturned(item, renter, TODAY.minusDays(7), TODAY.minusDays(6),
			TODAY.minusDays(6).atTime(18, 0));
		Rental overdue = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, TODAY.minusDays(5),
			TODAY.minusDays(4));

		// when
		int marked = job.markDue(NOW);

		// then
		assertThat(marked).isZero();
		assertThat(statusOf(dueToday)).isEqualTo(RentalStatus.RENTING);
		assertThat(statusOf(reserved)).isEqualTo(RentalStatus.RESERVED);
		assertThat(statusOf(returned)).isEqualTo(RentalStatus.RETURNED);
		assertThat(statusOf(overdue)).isEqualTo(RentalStatus.OVERDUE);
		assertThat(historyRepository.findAll()).isEmpty();
		assertThat(notificationPort.sent()).isEmpty();
	}

	@Test
	@DisplayName("자정이 지나면(00:00 재검사) 어제가 예정일인 건이 대상이 되고, 그 전날 23:59에는 아직 아니다")
	void midnightBoundary() {
		// given: 예정일 11-19
		Rental rental = renting(TODAY.minusDays(2), TODAY.minusDays(1));

		// when, then
		assertThat(job.markDue(TODAY.minusDays(1).atTime(23, 59))).isZero();
		assertThat(job.markDue(TODAY.atStartOfDay())).isEqualTo(1);
		assertThat(statusOf(rental)).isEqualTo(RentalStatus.OVERDUE);
	}

	@Test
	@DisplayName("결제 대기 연장이 있으면 건너뛰고(4-B), 연장이 만료된 뒤 매시 재검사에서 OVERDUE가 된다")
	void skipsWhilePendingExtension() {
		// given: 어제가 예정일이고, 어제 23:55에 연장을 요청해 00:10까지 결제 대기
		Rental rental = renting(TODAY.minusDays(3), TODAY.minusDays(1));
		extensionRepository.save(RentalExtension.pendingPayment(rental.getId(), TODAY.minusDays(1),
			TODAY.plusDays(1), 20_000, NOW.plusMinutes(5)));

		// when
		int markedAt0005 = job.markDue(NOW);

		// then
		assertThat(markedAt0005).isZero();
		assertThat(statusOf(rental)).isEqualTo(RentalStatus.RENTING);
		assertThat(notificationPort.sent()).isEmpty();

		// when: 01:00 재검사 (연장은 00:10에 만료, 만료 배치 전이어도 시각으로 본다)
		int markedAt0100 = job.markDue(NOW.plusMinutes(55));

		// then
		assertThat(markedAt0100).isEqualTo(1);
		assertThat(statusOf(rental)).isEqualTo(RentalStatus.OVERDUE);
		assertThat(notificationPort.sent()).hasSize(2);
	}

	private Rental renting(LocalDate start, LocalDate end) {
		return rentalFixture.createRental(item, renter, RentalStatus.RENTING, start, end);
	}

	private RentalStatus statusOf(Rental rental) {
		return rentalRepository.findById(rental.getId()).orElseThrow().getStatus();
	}
}
