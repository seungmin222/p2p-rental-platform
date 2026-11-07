package com.team7.rental.payment.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.global.storage.TestImages;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.batch.DeductionAutoAcceptJob;
import com.team7.rental.payment.batch.RefundRetryJob;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DeductionProposal;
import com.team7.rental.payment.entity.DeductionProposalStatus;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.repository.DeductionProposalRepository;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 보증금 보류·차감 (REQ-049). 물품 카테고리는 CAMPING(B 정액 10,000원, C 상한율 50%, D 전액), 대여료 30,000원 + 보증금 50,000원.
 * 보류는 상태 확인 '이상'(DepositReturnIntegrationTest)으로 들어온 것과 같게 결제 행을 직접 HELD로 둔다.
 */
class DepositDeductionIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final LocalDateTime HELD_AT = RETURNED_AT.plusHours(2);
	private static final LocalDateTime NOW = HELD_AT.plusHours(1);
	private static final int DEPOSIT = 50_000;
	private static final int RESPONSE_HOURS = 72;
	private static final int HOLD_DAYS = 7;

	@Autowired
	private DeductionAutoAcceptJob autoAcceptJob;

	@Autowired
	private RefundRetryJob retryJob;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	@Autowired
	private DeductionProposalRepository proposalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member owner;
	private Member renter;
	private Rental rental;
	private Payment payment;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
		payment = paymentFixture.createApproved(rental);
		payment.holdDeposit(HELD_AT);
		payment = paymentRepository.save(payment);
	}

	@Test
	@DisplayName("상한 조회: C는 floor(보증금 × 50%), 연체료가 있으면 남은 보증금까지, A는 400, 제3자는 403")
	void policy() throws Exception {
		getPolicy(owner, "C").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.deductionType").value("ACTUAL"))
			.andExpect(jsonPath("$.data.maxAmount").value(25_000))
			.andExpect(jsonPath("$.data.receiptRequired").value(true));
		getPolicy(renter, "B").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.maxAmount").value(10_000))
			.andExpect(jsonPath("$.data.receiptRequired").value(false));

		setOverdueFee(40_000);
		getPolicy(owner, "C").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.remainingDeposit").value(10_000))
			.andExpect(jsonPath("$.data.maxAmount").value(10_000));
		getPolicy(owner, "A").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_GRADE_NOT_ALLOWED"));
		getPolicy(fixture.createMember(), "C").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("C 제안은 증빙·영수증을 저장하고 PROPOSED로 남으며 대여자에게 DEDUCTION_PROPOSED, 상세에 최신 제안이 보인다")
	void propose() throws Exception {
		// when
		propose(owner, proposeJson("C", 20_000), evidence(), receipt()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.depositStatus").value("HELD"))
			.andExpect(jsonPath("$.data.latestDeductionProposal.status").value("PROPOSED"))
			.andExpect(jsonPath("$.data.latestDeductionProposal.amount").value(20_000))
			.andExpect(jsonPath("$.data.latestDeductionProposal.respondDeadline").value("2026-11-08T21:00:00"));

		// then
		DeductionProposal proposal = onlyProposal();
		assertThat(proposal.getProposedBy()).isEqualTo(owner.getId());
		assertThat(proposal.getGrade()).isEqualTo(DamageGrade.C);
		assertThat(proposal.getEvidenceUrls()).singleElement().asString().startsWith("/files/deductions/");
		assertThat(proposal.getReceiptUrls()).singleElement().asString().startsWith("/files/deductions/");
		assertThat(notificationPort.sent()).singleElement().satisfies(sent -> {
			assertThat(sent.memberId()).isEqualTo(renter.getId());
			assertThat(sent.type()).isEqualTo(Type.DEDUCTION_PROPOSED);
			assertThat(sent.eventId()).isEqualTo("DEDUCTION_PROPOSED:" + rental.getId() + ":" + proposal.getId());
		});
		// 대여자도 같은 제안을 본다
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(jsonPath("$.data.latestDeductionProposal.proposalId").value(proposal.getId()))
			.andExpect(jsonPath("$.data.deduction").doesNotExist());
	}

	@Test
	@DisplayName("제안 검증: 대여자 403, 영수증·증빙 없음 400, 상한 초과 400(details maxAmount), 보증금 초과 400, 진행 중 제안 409")
	void proposeValidation() throws Exception {
		propose(renter, proposeJson("B", 1_000), evidence()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_OWNER_ONLY"));
		propose(owner, proposeJson("C", 20_000), evidence()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_EVIDENCE_REQUIRED"));
		propose(owner, proposeJson("B", 5_000)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_EVIDENCE_REQUIRED"));
		propose(owner, proposeJson("C", 25_001), evidence(), receipt()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_EXCEEDS_CAP"))
			.andExpect(jsonPath("$.error.details[0].field").value("maxAmount"))
			.andExpect(jsonPath("$.error.details[0].reason").value("25000"));
		propose(owner, proposeJson("D", 50_001), evidence()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_EXCEEDS_DEPOSIT"));
		propose(owner, "{\"action\": \"PROPOSE\", \"grade\": \"B\", \"amount\": 1000}", evidence())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("reason"));
		assertThat(proposalRepository.count()).isZero();

		propose(owner, proposeJson("B", 10_000), evidence()).andExpect(status().isOk());
		propose(owner, proposeJson("B", 5_000), evidence()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_PROPOSAL_IN_PROGRESS"));
	}

	@Test
	@DisplayName("보류되지 않은 보증금(FROZEN)에는 제안할 수 없다(409 DEPOSIT_NOT_HELD)")
	void notHeld() throws Exception {
		ReflectionTestUtils.setField(payment, "depositStatus", DepositStatus.FROZEN);
		paymentRepository.save(payment);

		propose(owner, proposeJson("B", 1_000), evidence()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEPOSIT_NOT_HELD"));
	}

	@Test
	@DisplayName("수락하면 남은 보증금 − 차감액을 deposit-refund 키로 부분 취소하고 확정 결과를 남기며 COMPLETED, 양측에 DEDUCTION_ACCEPTED")
	void accept() throws Exception {
		// given
		setOverdueFee(5_000);
		Long proposalId = proposeOk(proposeJson("C", 20_000), evidence(), receipt());

		// when, then
		respond(renter, "ACCEPT", proposalId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.depositStatus").value("REFUNDED"))
			.andExpect(jsonPath("$.data.refundedAmount").value(25_000))
			.andExpect(jsonPath("$.data.deduction.grade").value("C"))
			.andExpect(jsonPath("$.data.deduction.amount").value(20_000));

		Payment settled = reloadPayment();
		assertThat(settled.getDamageGrade()).isEqualTo(DamageGrade.C);
		assertThat(settled.getDeductionAmount()).isEqualTo(20_000);
		assertThat(settled.getDeductionReason()).isEqualTo("수리비");
		assertThat(settled.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(25_000);
		assertThat(transactionRepository.findAll()).singleElement()
			.extracting(PaymentTransaction::getIdempotencyKey).isEqualTo("deposit-refund:" + rental.getId());
		assertThat(onlyProposal().getStatus()).isEqualTo(DeductionProposalStatus.ACCEPTED);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.COMPLETED);
			assertThat(history.getChangedBy()).isEqualTo(renter.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_DEDUCTION_ACCEPTED);
		});
		assertThat(sentOf(Type.DEDUCTION_ACCEPTED)).extracting(Sent::memberId)
			.containsExactlyInAnyOrder(owner.getId(), renter.getId());
	}

	@Test
	@DisplayName("남은 보증금 전액을 차감하면 PG 없이 FORFEITED·COMPLETED다")
	void acceptForfeits() throws Exception {
		setOverdueFee(10_000);
		Long proposalId = proposeOk(proposeJson("D", 40_000), evidence());

		respond(renter, "ACCEPT", proposalId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.depositStatus").value("FORFEITED"));

		assertThat(paymentGateway.cancelCalls()).isZero();
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	@DisplayName("부분 취소가 실패하면 RETURNED·REFUND_FAILED로 200, 재시도 배치가 REFUNDED·COMPLETED로 끝낸다")
	void acceptRefundFails() throws Exception {
		Long proposalId = proposeOk(proposeJson("B", 10_000), evidence());
		paymentGateway.failNextCancel(Fault.ERROR);

		respond(renter, "ACCEPT", proposalId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.depositStatus").value("REFUND_FAILED"))
			.andExpect(jsonPath("$.data.depositRefundPending").value(true));

		testClock.advance(java.time.Duration.ofMinutes(10));
		retryJob.retryDue(NOW.plusMinutes(10));
		assertThat(reloadPayment().getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(40_000);
	}

	@Test
	@DisplayName("응답 검증: 제공자 403, 다른 제안 ID 404, 사진을 보내면 400, 응답 기한이 지나면 409")
	void respondValidation() throws Exception {
		Long proposalId = proposeOk(proposeJson("B", 10_000), evidence());

		respond(owner, "ACCEPT", proposalId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_RENTER_ONLY"));
		respond(renter, "ACCEPT", proposalId + 100).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_PROPOSAL_NOT_FOUND"));
		propose(renter, "{\"action\": \"ACCEPT\", \"proposalId\": " + proposalId + "}", evidence())
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));
		propose(renter, "{\"action\": \"REJECT\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("proposalId"));

		testClock.advance(java.time.Duration.ofHours(RESPONSE_HOURS));
		respond(renter, "REJECT", proposalId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_PROPOSAL_CLOSED"));
		assertThat(onlyProposal().getStatus()).isEqualTo(DeductionProposalStatus.PROPOSED);
	}

	@Test
	@DisplayName("거절하면 REJECTED·보류 유지, 제공자에게 DEDUCTION_REJECTED, 제공자는 다시 제안할 수 있다")
	void rejectThenProposeAgain() throws Exception {
		Long proposalId = proposeOk(proposeJson("B", 10_000), evidence());

		respond(renter, "REJECT", proposalId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.depositStatus").value("HELD"))
			.andExpect(jsonPath("$.data.latestDeductionProposal.status").value("REJECTED"));

		assertThat(sentOf(Type.DEDUCTION_REJECTED)).singleElement().satisfies(sent -> {
			assertThat(sent.memberId()).isEqualTo(owner.getId());
			assertThat(sent.eventId()).isEqualTo("DEDUCTION_REJECTED:" + rental.getId() + ":" + proposalId);
		});
		propose(owner, proposeJson("B", 5_000), evidence()).andExpect(status().isOk());
		assertThat(proposalRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("자동 수락: 기한 1초 전은 그대로, 정확히 72시간이면 AUTO_ACCEPTED·확정(changedBy 없음), 다시 돌려도 한 번만")
	void autoAccept() throws Exception {
		// given
		proposeOk(proposeJson("B", 10_000), evidence());
		LocalDateTime deadline = NOW.plusHours(RESPONSE_HOURS);

		// when, then
		assertThat(autoAcceptJob.acceptDue(deadline.minusSeconds(1))).isZero();
		testClock.fixAt(deadline);
		assertThat(autoAcceptJob.acceptDue(deadline)).isEqualTo(1);
		assertThat(autoAcceptJob.acceptDue(deadline)).isZero();

		assertThat(onlyProposal().getStatus()).isEqualTo(DeductionProposalStatus.AUTO_ACCEPTED);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(40_000);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_DEDUCTION_AUTO_ACCEPT_BATCH);
		});
		assertThat(sentOf(Type.DEDUCTION_ACCEPTED)).hasSize(2);
	}

	@Test
	@DisplayName("관리자 목록은 보류 7일이 지난 건만, USER 토큰은 403이다")
	void heldList() throws Exception {
		Member admin = fixture.createMember();
		proposeOk(proposeJson("B", 10_000), evidence());

		heldList(admin).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
		testClock.fixAt(HELD_AT.plusDays(HOLD_DAYS));
		heldList(admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].rentalId").value(rental.getId()))
			.andExpect(jsonPath("$.data[0].remainingDeposit").value(DEPOSIT))
			.andExpect(jsonPath("$.data[0].latestProposal.status").value("PROPOSED"));
		mockMvc.perform(get("/api/admin/deposits/held").header(HttpHeaders.AUTHORIZATION,
			bearerToken(owner.getId()))).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("관리자 확정: 7일 전 409, 대여자 응답 대기 제안이 있으면 409, 거절이 이어진 뒤 확정하면 ADMIN_DECIDED·COMPLETED")
	void adminDecide() throws Exception {
		// given: 첫 제안은 바로 거절됐다
		Member admin = fixture.createMember();
		respond(renter, "REJECT", proposeOk(proposeJson("C", 25_000), evidence(), receipt()))
			.andExpect(status().isOk());
		String body = "{\"grade\": \"C\", \"amount\": 15000, \"reason\": \"증빙 검토 결과 일부 인정\"}";

		// when, then
		decide(admin, body).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEPOSIT_ADMIN_DECISION_TOO_EARLY"));
		testClock.fixAt(HELD_AT.plusDays(HOLD_DAYS));
		// 7일째 다시 제안하면 대여자 응답을 기다린다
		Long secondId = proposeOk(proposeJson("C", 20_000), evidence(), receipt());
		decide(admin, body).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DEDUCTION_PROPOSAL_IN_PROGRESS"));
		respond(renter, "REJECT", secondId).andExpect(status().isOk());
		decide(admin, "{\"grade\": \"C\", \"amount\": 25001, \"reason\": \"초과\"}")
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("DEDUCTION_EXCEEDS_CAP"));

		decide(admin, body).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.depositStatus").value("REFUNDED"))
			.andExpect(jsonPath("$.data.deduction.amount").value(15_000))
			.andExpect(jsonPath("$.data.latestProposal.status").value("ADMIN_DECIDED"));

		assertThat(paymentGateway.appliedCancels()).containsExactly(35_000);
		assertThat(proposalRepository.findAll()).extracting(DeductionProposal::getStatus)
			.containsExactly(DeductionProposalStatus.REJECTED, DeductionProposalStatus.REJECTED,
				DeductionProposalStatus.ADMIN_DECIDED);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getChangedBy()).isEqualTo(admin.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_DEDUCTION_ADMIN_DECIDED);
		});
		decide(admin, body).andExpect(status().isConflict());
		mockMvc.perform(patch("/api/admin/rentals/{id}/deposit", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
	}

	private Long proposeOk(String json, MockMultipartFile... files) throws Exception {
		propose(owner, json, files).andExpect(status().isOk());
		notificationPort.reset();
		return proposalRepository.findFirstByRentalIdOrderByIdDesc(rental.getId()).orElseThrow().getId();
	}

	private ResultActions propose(Member member, String json, MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, "/api/rentals/{id}/deposit",
			rental.getId());
		request.file(json(json));
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions respond(Member member, String action, Long proposalId) throws Exception {
		return propose(member, "{\"action\": \"" + action + "\", \"proposalId\": " + proposalId + "}");
	}

	private ResultActions getPolicy(Member member, String grade) throws Exception {
		return mockMvc.perform(get("/api/rentals/{id}/deposit/policy", rental.getId()).param("grade", grade)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions heldList(Member admin) throws Exception {
		return mockMvc.perform(get("/api/admin/deposits/held").header(HttpHeaders.AUTHORIZATION,
			adminBearerToken(admin.getId())));
	}

	private ResultActions decide(Member admin, String body) throws Exception {
		return mockMvc.perform(patch("/api/admin/rentals/{id}/deposit", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, adminBearerToken(admin.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String proposeJson(String grade, int amount) {
		return "{\"action\": \"PROPOSE\", \"grade\": \"" + grade + "\", \"amount\": " + amount
			+ ", \"reason\": \"수리비\"}";
	}

	static MockMultipartFile evidence() {
		return new MockMultipartFile("evidenceImages", "damage.jpg", MediaType.IMAGE_JPEG_VALUE,
			TestImages.jpeg(40, 30));
	}

	static MockMultipartFile receipt() {
		return new MockMultipartFile("receiptImages", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE,
			TestImages.jpeg(40, 30));
	}

	private void setOverdueFee(int overdueFee) {
		ReflectionTestUtils.setField(rental, "overdueFee", overdueFee);
		rental = rentalRepository.save(rental);
	}

	private DeductionProposal onlyProposal() {
		List<DeductionProposal> proposals = proposalRepository.findAll();
		assertThat(proposals).hasSize(1);
		return proposals.getFirst();
	}

	private List<Sent> sentOf(Type type) {
		return notificationPort.sent().stream().filter(sent -> sent.type() == type).toList();
	}

	private Rental reloadRental() {
		return rentalRepository.findById(rental.getId()).orElseThrow();
	}

	private Payment reloadPayment() {
		return paymentRepository.findById(payment.getId()).orElseThrow();
	}
}
