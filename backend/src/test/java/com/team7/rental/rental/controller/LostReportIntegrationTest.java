package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.batch.ReturnAutoConfirmJob;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 미반납 신고 (REQ-018·049, open-issues P4). 일 대여료 10,000원(연체 1일 15,000원), 보증금 50,000원, 종료일 11/5.
 * 신고는 오늘 &gt; 11/5 + 7일(11/12)부터, 즉 11/13부터다.
 */
class LostReportIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 9, 0);

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	@Autowired
	private ReturnAutoConfirmJob autoConfirmJob;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
	}

	@Test
	@DisplayName("동결 보증금이 있으면 사진 없는 RETURN 기록(lost_reported) → RETURNED·HELD, 연체료는 신고일 기준(보증금 한도), 대여자에게 LOST_REPORTED")
	void reportWithDeposit() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);

		// when, then
		report(owner, "{\"note\": \"연락이 닿지 않아요\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.depositStatus").value("HELD"))
			.andExpect(jsonPath("$.data.overdueFee").value(50_000));

		Rental reported = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(reported.getReturnedAt()).isEqualTo(NOW);
		// 8일 × 15,000원 = 120,000원 → 보증금 50,000원 한도
		assertThat(reported.getOverdueFee()).isEqualTo(50_000);
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getDepositStatus())
			.isEqualTo(DepositStatus.HELD);
		ItemInspection inspection = returnInspection();
		assertThat(inspection.isLostReported()).isTrue();
		assertThat(inspection.getImageUrls()).isEmpty();
		assertThat(inspection.getChecklist()).isNull();
		assertThat(inspection.getNote()).isEqualTo("연락이 닿지 않아요");
		assertThat(inspection.getWrittenBy()).isEqualTo(owner.getId());
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.OVERDUE);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RETURNED);
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_LOST_REPORT);
		});
		assertThat(notificationPort.sent()).singleElement().satisfies(sent -> {
			assertThat(sent.memberId()).isEqualTo(renter.getId());
			assertThat(sent.type()).isEqualTo(Type.LOST_REPORTED);
			assertThat(sent.eventId()).isEqualTo("LOST_REPORTED:" + rental.getId());
		});
	}

	@Test
	@DisplayName("동결 보증금이 없으면(결제 없음) 처리할 금액이 없어 바로 COMPLETED이고 이력이 두 건이다")
	void reportWithoutDeposit() throws Exception {
		report(owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		List<RentalStatusHistory> histories = historyRepository.findAll();
		assertThat(histories).extracting(RentalStatusHistory::getToStatus)
			.containsExactly(RentalStatus.RETURNED, RentalStatus.COMPLETED);
		assertThat(histories).extracting(RentalStatusHistory::getReason)
			.containsOnly(RentalStatusHistory.REASON_LOST_REPORT);
	}

	@Test
	@DisplayName("종료일 + 7일 당일까지는 409 RENTAL_LOST_REPORT_TOO_EARLY, 연체가 아니면 409, 대여자는 403, 메모가 길면 400")
	void validation() throws Exception {
		testClock.fixAt(END.plusDays(7).atTime(23, 59));
		report(owner, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_LOST_REPORT_TOO_EARLY"));
		testClock.fixAt(NOW);
		report(renter, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_OWNER_ONLY"));
		report(owner, "{\"note\": \"" + "가".repeat(301) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("note"));

		Rental renting = rentalFixture.createRental(item, renter, RentalStatus.RENTING, LocalDate.of(2026, 11, 6),
			LocalDate.of(2026, 11, 7));
		mockMvc.perform(post("/api/rentals/{id}/lost-report", renting.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
		assertThat(historyRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("신고 건은 물건이 돌아와도 상태 확인할 수 없고(409) 자동 상태 확인 대상도 아니며, D 아닌 등급은 제안할 수 없고 D는 사진 없이 제안한다")
	void reportedRentalEndsByDeduction() throws Exception {
		// given
		paymentFixture.createApproved(rental);
		report(owner, null).andExpect(status().isOk());

		// when, then
		confirm(json("{\"result\": \"NORMAL\"}")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
		testClock.advance(java.time.Duration.ofDays(30));
		assertThat(autoConfirmJob.confirmDue(NOW.plusDays(30))).isZero();

		deposit("{\"action\": \"PROPOSE\", \"grade\": \"C\", \"amount\": 0, \"reason\": \"분실\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_GRADE_NOT_ALLOWED"));
		// 연체료가 보증금 전액이라 남은 보증금 0원 → D 0원 제안
		deposit("{\"action\": \"PROPOSE\", \"grade\": \"D\", \"amount\": 0, \"reason\": \"분실\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.latestDeductionProposal.grade").value("D"))
			.andExpect(jsonPath("$.data.latestDeductionProposal.evidenceImageUrls").isEmpty());
	}

	private ResultActions report(Member member, String body) throws Exception {
		var request = post("/api/rentals/{id}/lost-report", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()));
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions confirm(org.springframework.mock.web.MockMultipartFile... parts) throws Exception {
		var request = multipart(HttpMethod.PATCH, "/api/rentals/{id}/return", rental.getId());
		for (var part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private ResultActions deposit(String json) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{id}/deposit", rental.getId())
			.file(json(json)).header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private ItemInspection returnInspection() {
		return inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN).orElseThrow();
	}
}
