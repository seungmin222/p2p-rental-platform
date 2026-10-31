package com.team7.rental.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentTransactionResult;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalRefundPort;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 취소 시 환불 (docs/domains/payment.md, REQ-015 연계). 대여료 30,000원 + 보증금 50,000원 = 80,000원, 수수료 30% = 9,000원 */
class RentalCancelRefundIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int AMOUNT = 80_000;
	private static final String CANCEL_BODY = "{\"reason\": \"SCHEDULE_CHANGE\"}";

	@Autowired
	private RentalRefundPort rentalRefundPort;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(LocalDateTime.of(2026, 10, 28, 10, 0));
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
	}

	@ParameterizedTest(name = "시작일 {0}일 전 대여자 취소 → 환불 {1}원, {2}")
	@CsvSource({
		"7, 80000, CANCELED",
		"3, 80000, CANCELED",
		"2, 71000, PARTIAL_CANCELED",
		"1, 71000, PARTIAL_CANCELED"
	})
	@DisplayName("대여자 취소의 환불액은 취소 수수료 표대로고, 전액이면 CANCELED, 일부면 PARTIAL_CANCELED이며 보증금은 REFUNDED다")
	void renterCancelBands(int daysBefore, int refund, PaymentStatus expected) throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		testClock.fixAt(START.minusDays(daysBefore).atTime(9, 0));

		// when, then
		cancel(renter).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelFee").value(AMOUNT - refund));

		Payment refunded = reload(payment);
		assertThat(refunded.getStatus()).isEqualTo(expected);
		assertThat(refunded.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(refunded.getRefundedAmount()).isEqualTo(refund);
		assertThat(refunded.getRefundPendingAmount()).isNull();
		assertThat(paymentGateway.appliedCancels()).containsExactly(refund);
	}

	@Test
	@DisplayName("제공자 취소는 시작일 직전이어도 전액 환불이다")
	void ownerCancelFull() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		testClock.fixAt(START.minusDays(1).atTime(9, 0));

		// when, then
		cancel(owner).andExpect(status().isOk());
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(AMOUNT);
	}

	@Test
	@DisplayName("양측 동의 취소는 수락할 때 전액 환불하고, 거절이면 환불하지 않는다")
	void mutualCancel() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		testClock.fixAt(START.atTime(9, 0));
		Number rejected = requestAgreement();

		// when: 거절
		respond(rejected, "REJECT").andExpect(status().isOk());

		// then
		assertThat(paymentGateway.cancelCalls()).isZero();

		// when: 수락
		Number accepted = requestAgreement();
		respond(accepted, "ACCEPT").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cancelType").value("MUTUAL"));

		// then
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(AMOUNT);
	}

	@Test
	@DisplayName("결제 없는 대여·결제 대기 취소는 PG를 부르지 않고, 결제 대기의 READY 주문은 FAILED가 된다")
	void noPaymentNoPg() throws Exception {
		// given
		Rental pending = rentalFixture.createPendingPayment(item, renter, START.plusDays(10), END.plusDays(10),
			LocalDateTime.of(2026, 10, 28, 10, 15));
		Payment ready = paymentRepository.save(
			Payment.createForRental(pending.getId(), "order-ready", "key-ready", 30_000, 50_000));

		// when, then
		cancel(renter).andExpect(status().isOk());
		mockMvc.perform(patch("/api/rentals/{id}/cancel", pending.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY)).andExpect(status().isOk());

		assertThat(paymentGateway.cancelCalls()).isZero();
		assertThat(reload(ready).getStatus()).isEqualTo(PaymentStatus.FAILED);
	}

	@Test
	@DisplayName("PG 취소가 실패해도 대여는 CANCELED이고 200이며, REFUND_FAILED로 환불액이 남는다")
	void pgFailureKeepsCanceled() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.ERROR);

		// when, then
		cancel(renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));

		Payment failed = reload(payment);
		assertThat(failed.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(failed.getDepositStatus()).isEqualTo(DepositStatus.REFUND_FAILED);
		assertThat(failed.getRefundPendingAmount()).isEqualTo(AMOUNT);
		assertThat(transactionRepository.findAll()).singleElement().satisfies((PaymentTransaction transaction) -> {
			assertThat(transaction.getAction()).isEqualTo(PaymentTransactionAction.CANCEL);
			assertThat(transaction.getIdempotencyKey()).isEqualTo("cancel-refund:" + rental.getId());
			assertThat(transaction.getResult()).isEqualTo(PaymentTransactionResult.FAIL);
		});
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.CANCELED);
	}

	@Test
	@DisplayName("보증금 0원 결제의 환불 실패는 보증금 NONE을 유지하고 환불액만 남는다")
	void noDepositFailureStaysNone() throws Exception {
		// given
		Rental noDeposit = rentalRepository.save(Rental.createReserved(item.getId(), owner.getId(), renter.getId(),
			START.plusDays(10), END.plusDays(10), 10_000, 0));
		Payment payment = paymentFixture.createApproved(noDeposit);
		paymentGateway.failNextCancel(Fault.NOT_APPLIED);

		// when
		mockMvc.perform(patch("/api/rentals/{id}/cancel", noDeposit.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY)).andExpect(status().isOk());

		// then
		Payment failed = reload(payment);
		assertThat(failed.getDepositStatus()).isEqualTo(DepositStatus.NONE);
		assertThat(failed.getRefundPendingAmount()).isEqualTo(30_000);
	}

	@Test
	@DisplayName("수수료가 결제 금액과 같아 환불액이 0원이면 예외 없이 PG를 부르지 않고 환불도 남기지 않는다")
	void zeroRefundNoPg() {
		// given: 보증금 0원, 수수료 = 대여료 (현재 수수료율로는 생기지 않지만 cancel-fee.rate-percent는 100까지 허용)
		Rental noDeposit = rentalRepository.save(Rental.createReserved(item.getId(), owner.getId(), renter.getId(),
			START.plusDays(10), END.plusDays(10), 10_000, 0));
		Payment payment = paymentFixture.createApproved(noDeposit);

		// when
		Optional<RefundTicket> ticket = rentalRefundPort.prepareCancelRefund(noDeposit.getId(), payment.getAmount());

		// then
		assertThat(ticket).isEmpty();
		assertThat(paymentGateway.cancelCalls()).isZero();
		Payment unchanged = reload(payment);
		assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(unchanged.getDepositStatus()).isEqualTo(DepositStatus.NONE);
		assertThat(unchanged.getRefundPendingAmount()).isNull();
		assertThat(transactionRepository.findAll()).isEmpty();
	}

	private ResultActions cancel(Member member) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY));
	}

	private Number requestAgreement() throws Exception {
		String body = mockMvc.perform(post("/api/rentals/{id}/cancel-agreements", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content(CANCEL_BODY))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.agreementId");
	}

	private ResultActions respond(Number agreementId, String action) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel-agreements/{agreementId}", rental.getId(), agreementId)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"" + action + "\"}"));
	}

	private Payment reload(Payment payment) {
		return paymentRepository.findById(payment.getId()).orElseThrow();
	}
}
