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
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 인계 리마인더 배치 (REQ-017, policies.md handover.reminder·handover.auto-cancel-days = 3). 오늘 = 2026-11-20 09:00,
 * 대상은 RESERVED이고 2026-11-17 &lt;= startDate &lt; 2026-11-20
 */
class HandoverReminderJobTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);
	private static final LocalDate TODAY = NOW.toLocalDate();

	@Autowired
	private HandoverReminderJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

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
	@DisplayName("어제 시작한 RESERVED 대여의 대여자·제공자에게 같은 event_id로 HANDOVER_REMINDER를 보내고 상태는 바꾸지 않는다")
	void remindsBothParticipants() {
		// given
		Rental target = reserved(TODAY.minusDays(1));
		String eventId = "HANDOVER_REMINDER:" + target.getId();

		// when
		int sent = job.remindDue(NOW);

		// then
		assertThat(sent).isEqualTo(1);
		assertThat(notificationPort.sent()).containsExactlyInAnyOrder(
			new Sent(renter.getId(), Type.HANDOVER_REMINDER, TargetType.RENTAL, target.getId(), eventId),
			new Sent(owner.getId(), Type.HANDOVER_REMINDER, TargetType.RENTAL, target.getId(), eventId));
		assertThat(rentalRepository.findById(target.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RESERVED);
		assertThat(historyRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("두 번 돌리거나 다음 날 다시 돌려도 알림은 회원별 1건만 저장된다")
	void rerunStoresOncePerMember() {
		// given
		Rental target = reserved(TODAY.minusDays(1));

		// when: 같은 회차 재실행 + 다음 날 회차
		job.remindDue(NOW);
		job.remindDue(NOW);
		testClock.fixAt(NOW.plusDays(1));
		job.remindDue(NOW.plusDays(1));

		// then
		assertThat(notificationPort.sent()).extracting(Sent::eventId)
			.containsOnly("HANDOVER_REMINDER:" + target.getId());
		assertThat(notificationRepository.findAll()).hasSize(2).allSatisfy(notification -> {
			assertThat(notification.getType()).isEqualTo(NotificationType.HANDOVER_REMINDER);
			assertThat(notification.getTargetId()).isEqualTo(target.getId());
		}).extracting(notification -> notification.getMemberId())
			.containsExactlyInAnyOrder(renter.getId(), owner.getId());
	}

	@Test
	@DisplayName("시작일이 2~3일 지난 미발송 건도 보낸다(배치를 빠뜨린 날 따라잡기)")
	void catchesUpWithinAutoCancelWindow() {
		// given
		Rental twoDaysAgo = reserved(TODAY.minusDays(2));
		Rental threeDaysAgo = reserved(TODAY.minusDays(3));

		// when
		int sent = job.remindDue(NOW);

		// then
		assertThat(sent).isEqualTo(2);
		assertThat(notificationPort.sent()).extracting(Sent::targetId)
			.containsExactlyInAnyOrder(twoDaysAgo.getId(), twoDaysAgo.getId(), threeDaysAgo.getId(),
				threeDaysAgo.getId());
	}

	@Test
	@DisplayName("오늘 시작·자동 취소 대상(시작일 4일 경과)·RESERVED가 아닌 대여에는 보내지 않는다")
	void skipsNonTargets() {
		// given
		reserved(TODAY);
		reserved(TODAY.minusDays(4));
		LocalDate yesterday = TODAY.minusDays(1);
		rentalFixture.createPendingPayment(item, renter, yesterday, yesterday.plusDays(2), NOW.plusMinutes(10));
		rentalFixture.createRental(item, renter, RentalStatus.RENTING, yesterday, yesterday.plusDays(2));
		rentalFixture.createRental(item, renter, RentalStatus.CANCELED, yesterday, yesterday.plusDays(2));

		// when
		int sent = job.remindDue(NOW);

		// then
		assertThat(sent).isZero();
		assertThat(notificationPort.sent()).isEmpty();
	}

	private Rental reserved(LocalDate start) {
		return rentalFixture.createRental(item, renter, RentalStatus.RESERVED, start, start.plusDays(2));
	}
}
