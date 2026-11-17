package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.batch.OverdueDetectJob;
import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalCancelTxService;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 앞 대여 연체로 이미 잡힌 다음 예약을 이행할 수 없는 경우 (open-issues #7). 다음 예약 11/3~11/5, 대여료 30,000원 + 보증금 50,000원 = 80,000원,
 * 평소 대여자 수수료 30% = 9,000원
 */
class PreviousRentalOverdueIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int AMOUNT = 80_000;
	private static final String EVENT_PREFIX = "PREVIOUS_RENTAL_OVERDUE:";

	@Autowired
	private OverdueDetectJob job;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RentalCancelAgreementRepository agreementRepository;

	@Autowired
	private RentalCancelTxService cancelTxService;

	private Member owner;
	private Member previousRenter;
	private Member renter;
	private Item item;
	private Rental next;

	@BeforeEach
	void setUp() {
		testClock.fixAt(LocalDateTime.of(2026, 10, 28, 10, 0));
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		previousRenter = fixture.createVerifiedMember().member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)).item();
		next = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
	}

	@Test
	@DisplayName("앞 대여가 연체된 채 다음 예약 시작일이 오면 다음 예약자에게만 PREVIOUS_RENTAL_OVERDUE를 1회 보낸다(재실행·매시 재검사 포함)")
	void notifiesNextRenterOnce() {
		// given: 앞 대여 종료일 11/2, 배치 전이라 아직 RENTING
		Rental previous = rentalFixture.createRental(item, previousRenter, RentalStatus.RENTING, START.minusDays(3),
			START.minusDays(1));
		LocalDateTime batchAt = START.atTime(0, 5);

		// when
		job.markDue(batchAt);
		job.markDue(batchAt);
		job.markDue(batchAt.plusHours(1));

		// then
		String eventId = EVENT_PREFIX + next.getId();
		assertThat(notificationPort.sent()).filteredOn(sent -> sent.type() == Type.PREVIOUS_RENTAL_OVERDUE)
			.containsOnly(new Sent(renter.getId(), Type.PREVIOUS_RENTAL_OVERDUE, TargetType.RENTAL, next.getId(),
				eventId));
		assertThat(notificationRepository.findAll()).filteredOn(n -> n.getEventId().startsWith(EVENT_PREFIX))
			.extracting(Notification::getMemberId, Notification::getEventId)
			.containsExactly(tuple(renter.getId(), eventId));
		// 앞 대여의 OVERDUE 알림은 그대로 (제공자는 이것으로 안다)
		assertThat(notificationPort.sent()).filteredOn(sent -> sent.type() == Type.OVERDUE)
			.extracting(Sent::targetId).containsOnly(previous.getId());
	}

	@Test
	@DisplayName("시작일 전인 예약, 결제 대기, 앞 대여가 반납된 예약은 알림 대상이 아니다")
	void skipsNonTargets() {
		// given: 앞 대여 연체 중, 다음 예약은 내일 시작
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(2));
		LocalDateTime dayBefore = START.minusDays(1).atTime(0, 5);
		// 결제 대기는 시작일이 오늘이어도 대상이 아니다(픽스처로 직접 만든 겹침)
		Rental pending = rentalFixture.createPendingPayment(item, renter, START.minusDays(1), START.minusDays(1),
			dayBefore.plusMinutes(10));
		testClock.fixAt(dayBefore);

		// when: 다음 예약(next)의 시작일 전날
		job.markDue(dayBefore);

		// then
		assertThat(notificationPort.sent()).filteredOn(sent -> sent.type() == Type.PREVIOUS_RENTAL_OVERDUE).isEmpty();
		assertThat(pending.getStatus()).isEqualTo(RentalStatus.PENDING_PAYMENT);
	}

	@Test
	@DisplayName("앞 대여가 반납(RETURNED)됐으면 시작일이 와도 알리지 않는다")
	void skipsWhenPreviousReturned() {
		// given
		rentalFixture.createReturned(item, previousRenter, START.minusDays(4), START.minusDays(2),
			START.atTime(0, 1));

		// when
		job.markDue(START.atTime(0, 5));

		// then
		assertThat(notificationPort.sent()).filteredOn(sent -> sent.type() == Type.PREVIOUS_RENTAL_OVERDUE).isEmpty();
	}

	@Test
	@DisplayName("앞 대여 연체 중 시작일 당일 대여자 단독 취소: 200, 수수료 0, 전액 환불, 이력 reason PREVIOUS_RENTAL_OVERDUE")
	void renterCancelOnStartDayFree() throws Exception {
		// given
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(1));
		Payment payment = paymentFixture.createApproved(next);
		testClock.fixAt(START.atTime(9, 0));
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(true));
		detail(owner).andExpect(jsonPath("$.data.previousRentalOverdue").value(true));

		// when, then
		cancel(renter).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelType").value("RENTER"))
			.andExpect(jsonPath("$.data.cancelReason").value("SCHEDULE_CHANGE"))
			.andExpect(jsonPath("$.data.cancelFee").value(0));

		Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(refunded.getRefundedAmount()).isEqualTo(AMOUNT);
		assertThat(paymentGateway.appliedCancels()).containsExactly(AMOUNT);
		assertThat(historyRepository.findAll()).singleElement()
			.satisfies((RentalStatusHistory history) -> {
				assertThat(history.getRentalId()).isEqualTo(next.getId());
				assertThat(history.getChangedBy()).isEqualTo(renter.getId());
				assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_PREVIOUS_RENTAL_OVERDUE);
			});
		// 취소된 뒤에는 false
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(false));
	}

	@Test
	@DisplayName("앞 대여 연체 중이어도 시작일 전날이면 플래그 false, 대여자 단독 취소는 기존 30% 수수료다")
	void renterCancelDayBeforeKeepsFee() throws Exception {
		// given: 오늘 11/2, 앞 대여 종료일 11/1(연체 중)
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(2));
		Payment payment = paymentFixture.createApproved(next);
		testClock.fixAt(START.minusDays(1).atTime(9, 0));
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(false));

		// when, then
		cancel(renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(9_000));
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getRefundedAmount())
			.isEqualTo(AMOUNT - 9_000);
		assertThat(historyRepository.findAll()).singleElement()
			.extracting(RentalStatusHistory::getReason)
			.isNotEqualTo(RentalStatusHistory.REASON_PREVIOUS_RENTAL_OVERDUE);
	}

	@Test
	@DisplayName("앞 대여 연체 중 시작일 다음 날에도 플래그 true, 대여자 단독 취소는 수수료 0이다")
	void renterCancelAfterStartFree() throws Exception {
		// given
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(1));
		testClock.fixAt(START.plusDays(1).atTime(9, 0));
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(true));

		// when, then
		cancel(renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(0));
	}

	@Test
	@DisplayName("동의 취소 요청(REQUESTED)이 진행 중이어도 면제 단독 취소는 된다. 남은 요청은 수락 시 409 INVALID_TRANSITION, 상세에 안 보이고 만료로 닫힌다")
	void exemptCancelWithOpenAgreement() throws Exception {
		// given: 시작일 당일, 대여자가 동의 취소를 요청해 둔 상태
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(1));
		LocalDateTime now = START.atTime(9, 0);
		testClock.fixAt(now);
		mockMvc.perform(post("/api/rentals/{id}/cancel-agreements", next.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"SCHEDULE_CHANGE\"}"))
			.andExpect(status().isCreated());
		RentalCancelAgreement agreement = agreementRepository.findAll().getFirst();

		// when: 면제 단독 취소
		cancel(renter).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelType").value("RENTER"))
			.andExpect(jsonPath("$.data.cancelFee").value(0))
			.andExpect(jsonPath("$.data.activeCancelAgreement").doesNotExist());

		// then: 제공자가 남은 요청을 수락해도 대여는 이미 CANCELED라 409이고 요청은 그대로 REQUESTED
		mockMvc.perform(patch("/api/rentals/{id}/cancel-agreements/{agreementId}", next.getId(), agreement.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"ACCEPT\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
		assertThat(agreementRepository.findById(agreement.getId()).orElseThrow().getStatus())
			.isEqualTo(CancelAgreementStatus.REQUESTED);
		// 만료 배치가 기존대로 닫는다
		assertThat(cancelTxService.expireOverdue(agreement.getExpiresAt())).isEqualTo(1);
		assertThat(agreementRepository.findById(agreement.getId()).orElseThrow().getStatus())
			.isEqualTo(CancelAgreementStatus.EXPIRED);
	}

	@Test
	@DisplayName("앞 대여가 종료일 당일(연체 아님)이면 시작일 전날 대여자 취소는 기존 수수료 그대로다")
	void renterCancelWhenPreviousNotOverdueYet() throws Exception {
		// given: 오늘 11/2 = 앞 대여 종료일
		rentalFixture.createRental(item, previousRenter, RentalStatus.RENTING, START.minusDays(3), START.minusDays(1));
		testClock.fixAt(START.minusDays(1).atTime(9, 0));
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(false));

		// when, then
		cancel(renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(9_000));
	}

	@Test
	@DisplayName("앞 대여가 반납된 뒤: 시작일 전날 취소는 수수료 표대로, 시작일 당일 단독 취소는 409 RENTAL_CANCEL_NOT_ALLOWED")
	void afterPreviousReturnedExistingRules() throws Exception {
		// given
		rentalFixture.createReturned(item, previousRenter, START.minusDays(4), START.minusDays(2),
			START.minusDays(1).atTime(8, 0));
		testClock.fixAt(START.atTime(9, 0));

		// when, then: 시작일 당일
		detail(renter).andExpect(jsonPath("$.data.previousRentalOverdue").value(false));
		cancel(renter).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_NOT_ALLOWED"));

		// 시작일 전날
		testClock.fixAt(START.minusDays(1).atTime(9, 0));
		cancel(renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(9_000));
	}

	@Test
	@DisplayName("앞 대여 연체 중에도 제공자 단독 취소는 기존 규칙(시작일 당일 409)이다")
	void ownerCancelUnchanged() throws Exception {
		// given
		rentalFixture.createRental(item, previousRenter, RentalStatus.OVERDUE, START.minusDays(4), START.minusDays(1));
		testClock.fixAt(START.atTime(9, 0));

		// when, then
		cancel(owner).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_NOT_ALLOWED"));
	}

	private ResultActions cancel(Member member) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel", next.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"SCHEDULE_CHANGE\"}"));
	}

	private ResultActions detail(Member member) throws Exception {
		return mockMvc.perform(get("/api/rentals/{id}", next.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())))
			.andExpect(status().isOk());
	}
}
