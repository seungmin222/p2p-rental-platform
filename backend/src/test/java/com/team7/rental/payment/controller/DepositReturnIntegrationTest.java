package com.team7.rental.payment.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.batch.RefundRetryJob;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.batch.ReturnAutoConfirmJob;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 보증금 반환·재시도 (REQ-025). 대여료 30,000원 + 보증금 50,000원 = 80,000원 */
class DepositReturnIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final LocalDateTime NOW = RETURNED_AT.plusDays(1);
	private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);
	private static final int DEPOSIT = 50_000;
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";
	private static final String ABNORMAL = "{\"result\": \"ABNORMAL\", \"resultNote\": \"모서리 파손\"}";

	@Autowired
	private RefundRetryJob retryJob;

	@Autowired
	private ReturnAutoConfirmJob autoConfirmJob;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member owner;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
	}

	@Test
	@DisplayName("정상 판정이면 보증금만 부분 취소하고(deposit-refund 키) REFUNDED·PARTIAL_CANCELED·COMPLETED다")
	void normalRefundsDeposit() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);

		// when, then
		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		Payment refunded = reload(payment);
		assertThat(refunded.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(refunded.getRefundedAmount()).isEqualTo(DEPOSIT);
		assertThat(paymentGateway.appliedCancels()).containsExactly(DEPOSIT);
		assertThat(transactionRepository.findAll()).singleElement()
			.extracting(PaymentTransaction::getIdempotencyKey).isEqualTo("deposit-refund:" + rental.getId());
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.COMPLETED);
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_CONFIRM_NORMAL);
		});
	}

	@Test
	@DisplayName("연체료가 있으면 보증금에서 뺀 금액만 반환한다")
	void overdueFeeDeducted() throws Exception {
		// given
		setOverdueFee(15_000);
		paymentFixture.createApproved(rental);

		// when
		confirm(json(NORMAL)).andExpect(status().isOk());

		// then
		assertThat(paymentGateway.appliedCancels()).containsExactly(35_000);
	}

	@Test
	@DisplayName("연체료가 보증금 전액이면 PG를 부르지 않고 FORFEITED·COMPLETED다")
	void zeroRefundForfeits() throws Exception {
		// given
		setOverdueFee(DEPOSIT);
		Payment payment = paymentFixture.createApproved(rental);

		// when, then
		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(reload(payment).getDepositStatus()).isEqualTo(DepositStatus.FORFEITED);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("보증금 0원 결제·결제 없는 대여는 PG를 부르지 않고 COMPLETED다")
	void noDepositNoPg() throws Exception {
		// given
		ReflectionTestUtils.setField(rental, "deposit", 0);
		rental = rentalRepository.save(rental);
		Payment payment = paymentFixture.createApproved(rental);

		// when, then
		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(reload(payment).getDepositStatus()).isEqualTo(DepositStatus.NONE);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("자동 상태 확인 배치도 보증금을 반환하고 COMPLETED로 끝낸다")
	void autoConfirmRefunds() {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		LocalDateTime dueAt = RETURNED_AT.plusDays(3);
		testClock.fixAt(dueAt);

		// when
		autoConfirmJob.confirmDue(dueAt);

		// then
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(reload(payment).getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_AUTO_CONFIRM_BATCH);
		});
	}

	@Test
	@DisplayName("이상 판정이고 보증금이 동결돼 있으면 RETURNED·FROZEN을 유지하고 PG를 부르지 않는다(REQ-049 전)")
	void abnormalKeepsFrozen() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);

		// when, then
		confirm(json(ABNORMAL), image()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"));

		assertThat(reload(payment).getDepositStatus()).isEqualTo(DepositStatus.FROZEN);
		assertThat(paymentGateway.cancelCalls()).isZero();
		assertThat(historyRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("반환 실패 → RETURNED·REFUND_FAILED, 200 → 재시도 성공 → REFUNDED·COMPLETED(이력은 배치)")
	void failThenRetrySucceeds() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.ERROR);

		// when, then
		confirm(json(NORMAL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RETURNED"));
		assertThat(reload(payment).getDepositStatus()).isEqualTo(DepositStatus.REFUND_FAILED);
		assertThat(reload(payment).getRefundPendingAmount()).isEqualTo(DEPOSIT);

		// 간격 전에는 재시도하지 않는다
		assertThat(retryJob.retryDue(NOW)).isZero();

		// when
		testClock.advance(RETRY_INTERVAL);
		retryJob.retryDue(NOW.plus(RETRY_INTERVAL));

		// then
		Payment refunded = reload(payment);
		assertThat(refunded.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(refunded.getRefundPendingAmount()).isNull();
		assertThat(refunded.getRefundedAmount()).isEqualTo(DEPOSIT);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(historyRepository.findAll()).singleElement()
			.extracting(RentalStatusHistory::getReason).isEqualTo(RentalStatusHistory.REASON_REFUND_RETRY_BATCH);
	}

	@Test
	@DisplayName("PG 응답이 늦어도 다음 재시도는 시도 시각 + 10분 정각에 돌고, 1초 전에는 돌지 않는다")
	void retryIntervalFromAttempt() throws Exception {
		// given: 시도는 NOW, PG 응답·결과 반영은 30초 뒤
		Payment payment = paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.ERROR);
		paymentGateway.beforeNextCancel(() -> testClock.advance(Duration.ofSeconds(30)));
		confirm(json(NORMAL)).andExpect(status().isOk());
		assertThat(reload(payment).getLastRefundAttemptAt()).isEqualTo(NOW);
		LocalDateTime dueAt = NOW.plus(RETRY_INTERVAL);

		// when, then
		testClock.fixAt(dueAt.minusSeconds(1));
		assertThat(retryJob.retryDue(dueAt.minusSeconds(1))).isZero();
		testClock.fixAt(dueAt);
		assertThat(retryJob.retryDue(dueAt)).isEqualTo(1);
		assertThat(reload(payment).getLastRefundAttemptAt()).isEqualTo(dueAt);
		assertThat(reload(payment).getRefundPendingAmount()).isNull();
	}

	@Test
	@DisplayName("응답이 유실된 뒤 같은 멱등성 키로 재시도하면 중복 환불이 0건이다")
	void lostResponseNoDuplicate() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.LOST_RESPONSE);
		confirm(json(NORMAL)).andExpect(status().isOk());
		assertThat(paymentGateway.appliedCancels()).containsExactly(DEPOSIT);

		// when
		testClock.advance(RETRY_INTERVAL);
		retryJob.retryDue(NOW.plus(RETRY_INTERVAL));

		// then
		assertThat(paymentGateway.cancelCalls()).isEqualTo(2);
		assertThat(paymentGateway.appliedCancels()).containsExactly(DEPOSIT);
		assertThat(reload(payment).getRefundedAmount()).isEqualTo(DEPOSIT);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.COMPLETED);
		assertThat(transactionRepository.findAll()).extracting(PaymentTransaction::getIdempotencyKey)
			.containsOnly("deposit-refund:" + rental.getId());
	}

	@Test
	@DisplayName("재시도 5회가 모두 실패하면 관리자 확인 대상이 되고 재시도에서 빠지며 대여는 RETURNED다")
	void retryLimit() throws Exception {
		// given
		Payment payment = paymentFixture.createApproved(rental);
		paymentGateway.failNextCancel(Fault.ERROR);
		confirm(json(NORMAL)).andExpect(status().isOk());
		LocalDateTime now = NOW;

		// when: 재시도 5회 실패 (첫 시도는 세지 않는다)
		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThat(reload(payment).isNeedsAdminCheck()).isFalse();
			paymentGateway.failNextCancel(Fault.ERROR);
			testClock.advance(RETRY_INTERVAL);
			now = now.plus(RETRY_INTERVAL);
			assertThat(retryJob.retryDue(now)).isEqualTo(1);
			assertThat(reload(payment).getRefundRetryCount()).isEqualTo(attempt);
		}

		// then
		assertThat(reload(payment).isNeedsAdminCheck()).isTrue();
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RETURNED);
		testClock.advance(RETRY_INTERVAL);
		assertThat(retryJob.retryDue(now.plus(RETRY_INTERVAL))).isZero();
		assertThat(paymentGateway.cancelCalls()).isEqualTo(6);
		assertThat(paymentGateway.appliedCancels()).isEmpty();
	}

	@Test
	@DisplayName("취소 환불의 재시도는 대여 상태를 바꾸지 않고 환불만 끝낸다")
	void cancelRefundRetry() {
		// given: 취소된 대여의 환불이 남아 있다
		ReflectionTestUtils.setField(rental, "status", RentalStatus.CANCELED);
		rental = rentalRepository.save(rental);
		Payment payment = paymentFixture.createApproved(rental);
		payment.requestRefund(80_000, NOW);
		paymentRepository.save(payment);
		transactionRepository.save(PaymentTransaction.pending(payment.getId(), PaymentTransactionAction.CANCEL, 80_000,
			"cancel-refund:" + rental.getId()));

		// when
		testClock.advance(RETRY_INTERVAL);
		retryJob.retryDue(NOW.plus(RETRY_INTERVAL));

		// then
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(historyRepository.findAll()).isEmpty();
	}

	private void setOverdueFee(int overdueFee) {
		// REQ-023 전이라 값을 넣는 API가 없다
		ReflectionTestUtils.setField(rental, "overdueFee", overdueFee);
		rental = rentalRepository.save(rental);
	}

	private ResultActions confirm(MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return",
			rental.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private Rental reloadRental() {
		return rentalRepository.findById(rental.getId()).orElseThrow();
	}

	private Payment reload(Payment payment) {
		return paymentRepository.findById(payment.getId()).orElseThrow();
	}
}
