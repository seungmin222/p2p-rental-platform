package com.team7.rental.payment.controller;

import static com.team7.rental.payment.controller.DepositDeductionIntegrationTest.*;
import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
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

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.batch.DeductionAutoAcceptJob;
import com.team7.rental.payment.batch.RefundRetryJob;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.batch.ReturnAutoConfirmJob;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalSettlementPort;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 정산 생성 경로 (REQ-039). 거래 완료가 되는 모든 경로와 수수료 있는 취소에서 정산이 한 건만 생기고, 받은 돈이 없으면 생기지 않는지 본다.
 * 대여료 30,000원(3일 × 10,000원) + 보증금 50,000원, 수수료 10%. 2026-11-05는 목요일이다.
 */
class SettlementCreationIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	/** 금요일 */
	private static final LocalDateTime NOW = RETURNED_AT.plusDays(1);
	/** 금요일 완료 → 영업일 +2 = 화요일 */
	private static final LocalDate FRIDAY_PAYOUT = LocalDate.of(2026, 11, 10);
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";
	private static final String ABNORMAL = "{\"result\": \"ABNORMAL\", \"resultNote\": \"모서리 파손\"}";
	private static final String CANCEL_BODY = "{\"reason\": \"SCHEDULE_CHANGE\"}";

	@Autowired
	private SettlementRepository settlementRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RefundRetryJob retryJob;

	@Autowired
	private DeductionAutoAcceptJob autoAcceptJob;

	@Autowired
	private ReturnAutoConfirmJob autoConfirmJob;

	@Autowired
	private RentalSettlementPort rentalSettlementPort;

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
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
	}

	@Test
	@DisplayName("경로 1: 정상 판정 후 보증금 반환이 끝나면 정산 1건, 금요일 완료라 지급 예정일은 화요일이다")
	void normalWithRefund() throws Exception {
		paymentFixture.createApproved(rental);

		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 0, 27_000, FRIDAY_PAYOUT);
		assertThat(onlySettlement().getOwnerId()).isEqualTo(owner.getId());
		assertThat(onlySettlement().getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
	}

	@Test
	@DisplayName("경로 1(토요일 자동 확인): 토요일 완료도 지급 예정일은 화요일이다")
	void autoConfirmOnSaturday() {
		paymentFixture.createApproved(rental);
		LocalDateTime saturday = LocalDateTime.of(2026, 11, 7, 10, 0);
		ReflectionTestUtils.setField(rental, "returnedAt", saturday.minusDays(3));
		rental = rentalRepository.save(rental);
		testClock.fixAt(saturday);

		assertThat(autoConfirmJob.confirmDue(saturday)).isEqualTo(1);

		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 0, 27_000, LocalDate.of(2026, 11, 10));
	}

	@Test
	@DisplayName("경로 2: 보증금 반환이 실패하면 정산이 없고, 재시도가 성공해 완료될 때 1건 생긴다")
	void refundRetry() throws Exception {
		paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.ERROR);

		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RETURNED"));
		assertThat(settlementRepository.findAll()).isEmpty();

		testClock.advance(Duration.ofMinutes(10));
		retryJob.retryDue(NOW.plusMinutes(10));

		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 0, 27_000, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 3: 연체료와 손상 차감이 둘 다 있는 차감 수락은 연체료에만 수수료를 떼고 차감액을 더한다(이중 계산 없음)")
	void acceptWithOverdueAndDeduction() throws Exception {
		Payment payment = holdDeposit(15_000);

		Long proposalId = proposeOk(proposeJson("C", 20_000), evidence(), receipt());
		deposit(renter, "{\"action\": \"ACCEPT\", \"proposalId\": " + proposalId + "}").andExpect(status().isOk());

		// 반환액 = 50,000 − 15,000 − 20,000 = 15,000. payments.deduction_amount에는 손상 차감액만 있다
		Payment settled = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(settled.getDeductionAmount()).isEqualTo(20_000);
		assertThat(paymentGateway.appliedCancels()).containsExactly(15_000);
		// gross = 30,000 + 15,000, fee = 4,500, payout = 45,000 − 4,500 + 20,000
		assertSettlement(SettlementSource.COMPLETED, 45_000, 4_500, 20_000, 60_500, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 3(자동 수락): 72시간 무응답 자동 수락으로 완료돼도 정산 1건")
	void autoAccept() throws Exception {
		holdDeposit(0);
		proposeOk(proposeJson("C", 10_000), evidence(), receipt());

		LocalDateTime deadline = NOW.plusHours(72);
		testClock.fixAt(deadline);
		assertThat(autoAcceptJob.acceptDue(deadline)).isEqualTo(1);

		// 월요일 완료 → 수요일
		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 10_000, 37_000, LocalDate.of(2026, 11, 11));
	}

	@Test
	@DisplayName("경로 3(관리자 확정): 보류 7일 뒤 관리자 확정으로 완료돼도 정산 1건")
	void adminDecide() throws Exception {
		holdDeposit(0);
		Member admin = fixture.createMember();
		LocalDateTime decideAt = NOW.plusDays(7);
		testClock.fixAt(decideAt);

		mockMvc.perform(patch("/api/admin/rentals/{id}/deposit", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, adminBearerToken(admin.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"grade\": \"C\", \"amount\": 15000, \"reason\": \"일부 인정\"}"))
			.andExpect(status().isOk());

		// 금요일 완료 → 화요일
		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 15_000, 42_000, LocalDate.of(2026, 11, 17));
	}

	@Test
	@DisplayName("경로 4: 연체료 + 차감으로 반환액 0원(FORFEITED)이면 PG 없이 완료되고 정산 1건")
	void forfeitedByDeduction() throws Exception {
		Payment payment = holdDeposit(15_000);

		Long proposalId = proposeOk(proposeJson("D", 35_000), evidence());
		deposit(renter, "{\"action\": \"ACCEPT\", \"proposalId\": " + proposalId + "}").andExpect(status().isOk());

		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getDepositStatus())
			.isEqualTo(DepositStatus.FORFEITED);
		assertThat(paymentGateway.cancelCalls()).isZero();
		assertSettlement(SettlementSource.COMPLETED, 45_000, 4_500, 35_000, 75_500, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 5: 연체료가 보증금 전액이면 정상 판정이 PG 없이 완료되고 연체료까지 정산한다")
	void normalForfeitedByOverdue() throws Exception {
		setOverdueFee(50_000);
		paymentFixture.createApproved(rental);

		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(paymentGateway.cancelCalls()).isZero();
		assertSettlement(SettlementSource.COMPLETED, 80_000, 8_000, 0, 72_000, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 5: 보증금 0원 결제의 정상 판정은 바로 완료되고 정산 1건. 같은 대여에 다시 불려도 1건이다")
	void normalWithoutDeposit() throws Exception {
		setDeposit(0);
		paymentFixture.createApproved(rental);

		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
		rentalSettlementPort.createForCompletion(rental.getId(), owner.getId(), 0, NOW);

		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 0, 27_000, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 6: 동결된 보증금 없이 이상 판정으로 완료돼도 정산 1건")
	void abnormalWithoutDeposit() throws Exception {
		setDeposit(0);
		paymentFixture.createApproved(rental);

		confirm(json(ABNORMAL), image()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertSettlement(SettlementSource.COMPLETED, 30_000, 3_000, 0, 27_000, FRIDAY_PAYOUT);
	}

	@Test
	@DisplayName("경로 7: 동결된 보증금 없이 미반납 신고로 완료돼도 정산 1건")
	void lostReportWithoutDeposit() throws Exception {
		Rental overdue = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		ReflectionTestUtils.setField(overdue, "deposit", 0);
		overdue = rentalRepository.save(overdue);
		paymentFixture.createApproved(overdue);
		// 금요일 > 예정일 + 7일
		LocalDateTime reportAt = LocalDateTime.of(2026, 11, 13, 10, 0);
		testClock.fixAt(reportAt);

		mockMvc.perform(post("/api/rentals/{id}/lost-report", overdue.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());

		assertThat(rentalRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.COMPLETED);
		Settlement settlement = settlementRepository.findByRentalId(overdue.getId()).orElseThrow();
		assertThat(settlement.getPayoutAmount()).isEqualTo(27_000);
		assertThat(settlement.getScheduledDate()).isEqualTo(LocalDate.of(2026, 11, 17));
	}

	@Test
	@DisplayName("경로 8: 수수료 있는 취소는 CANCEL_FEE 정산 1건이고, 대여자 환불이 REFUND_FAILED로 남아도 그대로다")
	void cancelFeeWithRefundFailure() throws Exception {
		Rental reserved = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START.plusDays(10),
			END.plusDays(10));
		Payment payment = paymentFixture.createApproved(reserved);
		// 시작일(11/13) 2일 전 수요일 → 수수료 30% = 9,000원
		LocalDateTime cancelAt = LocalDateTime.of(2026, 11, 11, 9, 0);
		testClock.fixAt(cancelAt);
		paymentGateway.failNextCancel(Fault.ERROR);

		cancel(reserved).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(9_000));

		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getDepositStatus())
			.isEqualTo(DepositStatus.REFUND_FAILED);
		Settlement settlement = settlementRepository.findByRentalId(reserved.getId()).orElseThrow();
		assertThat(settlement.getSource()).isEqualTo(SettlementSource.CANCEL_FEE);
		assertThat(settlement.getGrossAmount()).isEqualTo(9_000);
		assertThat(settlement.getFeeAmount()).isEqualTo(900);
		assertThat(settlement.getDeductionAmount()).isZero();
		assertThat(settlement.getPayoutAmount()).isEqualTo(8_100);
		// 수요일 → 금요일
		assertThat(settlement.getScheduledDate()).isEqualTo(LocalDate.of(2026, 11, 13));
	}

	@Test
	@DisplayName("연장료는 실제로 받은 금액만 더한다(REQ-038): 승인 20,000원은 들어가고, 환불 중(실패 포함)·전액 환불된 연장 결제는 빠진다")
	void extensionFeesActuallyCollected() {
		// given: 대여 결제 + 확정된 연장 20,000원 + 확정 못 해 환불이 실패한 연장 10,000원 + 전액 환불된 연장 5,000원
		paymentFixture.createApproved(rental);
		paymentRepository.save(approvedExtension(20_000, "ext-1"));
		Payment refundFailed = approvedExtension(10_000, "ext-2");
		refundFailed.requestRefund(10_000, NOW);
		refundFailed.markRefundFailed();
		paymentRepository.save(refundFailed);
		Payment refunded = approvedExtension(5_000, "ext-3");
		refunded.requestRefund(5_000, NOW);
		refunded.applyRefund();
		paymentRepository.save(refunded);

		// when
		rentalSettlementPort.createForCompletion(rental.getId(), owner.getId(), 0, NOW);

		// then: gross = 대여료 30,000 + 연장료 20,000
		assertSettlement(SettlementSource.COMPLETED, 50_000, 5_000, 0, 45_000, FRIDAY_PAYOUT);
	}

	private Payment approvedExtension(int fee, String orderId) {
		Payment payment = Payment.createForExtension(rental.getId(), orderId, "key-" + orderId, fee);
		payment.approve("pay-" + orderId);
		return payment;
	}

	@Test
	@DisplayName("만들지 않음: 결제 없이 완료된 대여(결제 기능 꺼짐)는 정상 판정이 실패하지 않고 정산이 없다")
	void noPaymentCompletion() throws Exception {
		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(settlementRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("만들지 않음: 결제 없이 미반납 신고·이상 판정으로 완료돼도 실패하지 않고 정산이 없다")
	void noPaymentLostReportAndAbnormal() throws Exception {
		confirm(json(ABNORMAL), image()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"));
		Rental overdue = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		testClock.fixAt(LocalDateTime.of(2026, 11, 13, 10, 0));
		mockMvc.perform(post("/api/rentals/{id}/lost-report", overdue.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());

		assertThat(rentalRepository.findById(overdue.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.COMPLETED);
		assertThat(settlementRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("만들지 않음: 대여료 0원(보증금만 결제)이고 차감이 없으면 지급액 0원이라 정산이 없다")
	void freeRentalNoPayout() throws Exception {
		ReflectionTestUtils.setField(rental, "rentalFee", 0);
		rental = rentalRepository.save(rental);
		paymentFixture.createApproved(rental);

		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(settlementRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("만들지 않음: 수수료 없는 취소, 결제 없이 수수료가 계산된 취소는 정산이 없다")
	void cancelWithoutSettlement() throws Exception {
		Rental early = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START.plusDays(10),
			END.plusDays(10));
		paymentFixture.createApproved(early);
		Rental unpaid = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START.plusDays(20),
			END.plusDays(20));
		// early: 시작일(11/13) 7일 전 → 수수료 없음
		testClock.fixAt(LocalDateTime.of(2026, 11, 6, 9, 0));
		cancel(early).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancelFee").value(0));
		// unpaid: 시작일(11/23) 1일 전 → 수수료는 계산되지만 받은 돈이 없다
		testClock.fixAt(LocalDateTime.of(2026, 11, 22, 9, 0));
		cancel(unpaid).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));

		assertThat(settlementRepository.findAll()).isEmpty();
	}

	private Payment holdDeposit(int overdueFee) {
		setOverdueFee(overdueFee);
		Payment payment = paymentFixture.createApproved(rental);
		payment.holdDeposit(RETURNED_AT.plusHours(2));
		return paymentRepository.save(payment);
	}

	private void setOverdueFee(int overdueFee) {
		ReflectionTestUtils.setField(rental, "overdueFee", overdueFee);
		rental = rentalRepository.save(rental);
	}

	private void setDeposit(int deposit) {
		ReflectionTestUtils.setField(rental, "deposit", deposit);
		rental = rentalRepository.save(rental);
	}

	private static String proposeJson(String grade, int amount) {
		return "{\"action\": \"PROPOSE\", \"grade\": \"" + grade + "\", \"amount\": " + amount
			+ ", \"reason\": \"수리비\"}";
	}

	private Long proposeOk(String json, MockMultipartFile... files) throws Exception {
		String body = deposit(owner, json, files).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString();
		Number id = JsonPath.read(body, "$.data.latestDeductionProposal.proposalId");
		return id.longValue();
	}

	private ResultActions deposit(Member member, String json, MockMultipartFile... files) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, "/api/rentals/{id}/deposit",
			rental.getId());
		request.file(json(json));
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions confirm(MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return",
			rental.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private ResultActions cancel(Rental target) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel", target.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY));
	}

	private Settlement onlySettlement() {
		List<Settlement> settlements = settlementRepository.findAll();
		assertThat(settlements).hasSize(1);
		return settlements.getFirst();
	}

	private void assertSettlement(SettlementSource source, int gross, int fee, int deduction, int payout,
		LocalDate scheduledDate) {
		Settlement settlement = onlySettlement();
		assertThat(settlement.getRentalId()).isEqualTo(rental.getId());
		assertThat(settlement.getSource()).isEqualTo(source);
		assertThat(settlement.getGrossAmount()).isEqualTo(gross);
		assertThat(settlement.getFeeAmount()).isEqualTo(fee);
		assertThat(settlement.getDeductionAmount()).isEqualTo(deduction);
		assertThat(settlement.getPayoutAmount()).isEqualTo(payout);
		assertThat(settlement.getScheduledDate()).isEqualTo(scheduledDate);
	}

	private Rental reloadRental() {
		return rentalRepository.findById(rental.getId()).orElseThrow();
	}
}
