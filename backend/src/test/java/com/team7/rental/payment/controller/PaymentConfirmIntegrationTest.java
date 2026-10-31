package com.team7.rental.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import com.team7.rental.payment.entity.PaymentTransactionResult;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** POST /api/payments, POST /api/payments/confirm (REQ-024). 대여료 10,000원 × 3일 + 보증금 50,000원 = 80,000원 */
class PaymentConfirmIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDateTime EXPIRES_AT = NOW.plusMinutes(15);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int AMOUNT = 80_000;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

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
		rental = rentalFixture.createPendingPayment(item, renter, START, END, EXPIRES_AT);
	}

	@Test
	@DisplayName("주문을 만들면 서버가 계산한 금액을 돌려주고 READY로 저장하며, 다시 부르면 같은 주문이다")
	void createOrder() throws Exception {
		// when
		String orderId = createOrder(renter, rental).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.amount").value(AMOUNT))
			.andExpect(jsonPath("$.data.orderName").value("테스트 물품"))
			.andExpect(jsonPath("$.data.customerKey").isNotEmpty())
			.andReturn().getResponse().getContentAsString();
		String second = createOrder(renter, rental).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		// then
		assertThat(JsonPath.<String>read(second, "$.data.orderId"))
			.isEqualTo(JsonPath.<String>read(orderId, "$.data.orderId"));
		assertThat(paymentRepository.findAll()).singleElement().satisfies(payment -> {
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(payment.getRentalFeeAmount()).isEqualTo(30_000);
			assertThat(payment.getDepositAmount()).isEqualTo(50_000);
		});
	}

	@Test
	@DisplayName("대여자가 아니면 403, 없는 대여면 404, 만료됐으면 409다")
	void createOrderGuards() throws Exception {
		createOrder(owner, rental).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_RENTER_ONLY"));

		mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"rentalId\": 999999}"))
			.andExpect(status().isNotFound());

		testClock.fixAt(EXPIRES_AT);
		createOrder(renter, rental).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_EXPIRED"));
	}

	@Test
	@DisplayName("0원 대여(바로 RESERVED)는 주문을 만들 수 없고 PG를 부르지 않는다")
	void zeroAmountDoesNotCallPg() throws Exception {
		// given
		Rental free = rentalRepository.save(Rental.createReserved(item.getId(), owner.getId(), renter.getId(),
			START.plusDays(10), END.plusDays(10), 0, 0));

		// when, then
		createOrder(renter, free).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
		assertThat(paymentRepository.findAll()).isEmpty();
		assertThat(paymentGateway.confirmCalls()).isZero();
	}

	@Test
	@DisplayName("승인 성공: 결제 APPROVED·보증금 FROZEN, 대여 RESERVED, 시도 SUCCESS, 이력은 시스템(PAYMENT_APPROVED)이다")
	void confirmSuccess() throws Exception {
		// given
		String orderId = orderIdOf(rental);

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.depositStatus").value("FROZEN"))
			.andExpect(jsonPath("$.data.rentalId").value(rental.getId()));

		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(onlyPayment().getPaymentKey()).isEqualTo("pay-key");
		assertThat(transactionRepository.findAll()).singleElement()
			.extracting(PaymentTransaction::getResult).isEqualTo(PaymentTransactionResult.SUCCESS);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RESERVED);
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_PAYMENT_APPROVED);
		});
	}

	@Test
	@DisplayName("보증금 0원이면 승인 후 보증금 상태가 NONE이다")
	void confirmNoDeposit() throws Exception {
		// given
		Rental noDeposit = rentalRepository.save(Rental.createPendingPayment(item.getId(), owner.getId(),
			renter.getId(), START.plusDays(10), END.plusDays(10), 10_000, 0, EXPIRES_AT));

		// when, then
		confirm(renter, orderIdOf(noDeposit), 30_000).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.depositStatus").value("NONE"));
	}

	@Test
	@DisplayName("이미 승인된 주문을 다시 승인하면 기존 결과를 돌려주고 PG를 또 부르지 않는다")
	void confirmIdempotent() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		confirm(renter, orderId, AMOUNT).andExpect(status().isOk());

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("APPROVED"));
		assertThat(paymentGateway.confirmCalls()).isEqualTo(1);
	}

	@Test
	@DisplayName("승인을 동시에 두 번 보내도(더블 클릭) 한 번만 승인된다")
	void confirmDoubleClick() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> futures = List.of(1, 2).stream().map(i -> executor.submit(() -> {
				start.await();
				return confirm(renter, orderId, AMOUNT).andReturn().getResponse().getStatus();
			})).toList();

			// when
			start.countDown();

			// then
			for (Future<Integer> future : futures) {
				assertThat(future.get()).isEqualTo(200);
			}
		}
		assertThat(paymentGateway.appliedConfirms()).isEqualTo(1);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(onlyPayment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
	}

	@Test
	@DisplayName("금액이 다르면 400, 결제 FAILED·대여 CANCELED(AMOUNT_MISMATCH)가 커밋되고 PG를 부르지 않는다")
	void amountMismatch() throws Exception {
		// given
		String orderId = orderIdOf(rental);

		// when, then
		confirm(renter, orderId, AMOUNT - 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_AMOUNT_MISMATCH"));

		assertThat(paymentGateway.confirmCalls()).isZero();
		assertThat(onlyPayment().getStatus()).isEqualTo(PaymentStatus.FAILED);
		Rental canceled = reloadRental();
		assertThat(canceled.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(canceled.getCancelType()).isEqualTo(CancelType.AMOUNT_MISMATCH);
	}

	@Test
	@DisplayName("PG 오류면 400, 결제 FAILED·시도 FAIL이고 대여는 PENDING_PAYMENT로 남아 새 주문으로 다시 결제할 수 있다")
	void pgError() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		paymentGateway.failNextConfirm(Fault.ERROR);

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_APPROVAL_FAILED"));

		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.PENDING_PAYMENT);
		assertThat(onlyPayment().getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(transactionRepository.findAll()).singleElement()
			.extracting(PaymentTransaction::getResult).isEqualTo(PaymentTransactionResult.FAIL);

		// 같은 주문은 다시 쓸 수 없고 새 주문으로 결제한다
		confirm(renter, orderId, AMOUNT).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_INVALID_STATUS"));
		String newOrderId = orderIdOf(rental);
		assertThat(newOrderId).isNotEqualTo(orderId);
		confirm(renter, newOrderId, AMOUNT).andExpect(status().isOk());
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("응답이 유실돼도 조회로 승인을 확인해 반영하고 중복 승인이 없다")
	void lostResponse() throws Exception {
		// given
		paymentGateway.failNextConfirm(Fault.LOST_RESPONSE);

		// when, then
		confirm(renter, orderIdOf(rental), AMOUNT).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("APPROVED"));
		assertThat(paymentGateway.lookups()).isEqualTo(1);
		assertThat(paymentGateway.appliedConfirms()).isEqualTo(1);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("결과를 알 수 없으면 503, READY·시도 PENDING으로 남고, 같은 요청을 다시 보내면 한 번만 승인된다")
	void unknownThenRetry() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		paymentGateway.failNextConfirm(Fault.NOT_APPLIED);

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_RESULT_UNKNOWN"));
		assertThat(onlyPayment().getStatus()).isEqualTo(PaymentStatus.READY);
		assertThat(transactionRepository.findAll()).singleElement()
			.extracting(PaymentTransaction::getResult).isEqualTo(PaymentTransactionResult.PENDING);

		confirm(renter, orderId, AMOUNT).andExpect(status().isOk());
		assertThat(paymentGateway.appliedConfirms()).isEqualTo(1);
		assertThat(reloadRental().getStatus()).isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("승인 호출 전에 이미 만료됐으면 409이고 PG를 부르지 않는다")
	void expiredBeforeConfirm() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		testClock.fixAt(EXPIRES_AT);

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_EXPIRED"));
		assertThat(paymentGateway.confirmCalls()).isZero();
	}

	@Test
	@DisplayName("승인 중 만료 시각이 지나면(경합) 대여를 확정하지 않고 CANCELED(PAYMENT_EXPIRED) + 전액 취소한다")
	void expiredDuringConfirm() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		paymentGateway.beforeNextConfirm(() -> testClock.advance(Duration.ofMinutes(16)));

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_CANCELED_AFTER_EXPIRY"));

		Rental canceled = reloadRental();
		assertThat(canceled.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(canceled.getCancelType()).isEqualTo(CancelType.PAYMENT_EXPIRED);
		Payment payment = onlyPayment();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(payment.getRefundedAmount()).isEqualTo(AMOUNT);
		assertThat(payment.getRefundPendingAmount()).isNull();
		assertThat(paymentGateway.appliedCancels()).containsExactly(AMOUNT);
	}

	@Test
	@DisplayName("경합의 전액 취소가 실패하면 REFUND_FAILED로 환불액이 남아 재시도 대상이 된다")
	void expiredDuringConfirmRefundFails() throws Exception {
		// given
		String orderId = orderIdOf(rental);
		paymentGateway.beforeNextConfirm(() -> testClock.advance(Duration.ofMinutes(16)));
		paymentGateway.failNextCancel(Fault.ERROR);

		// when, then
		confirm(renter, orderId, AMOUNT).andExpect(status().isConflict());

		Payment payment = onlyPayment();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.REFUND_FAILED);
		assertThat(payment.getRefundPendingAmount()).isEqualTo(AMOUNT);
	}

	private String orderIdOf(Rental target) throws Exception {
		String body = createOrder(renter, target).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.orderId");
	}

	private ResultActions createOrder(Member member, Rental target) throws Exception {
		return mockMvc.perform(post("/api/payments")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"rentalId\": " + target.getId() + "}"));
	}

	private ResultActions confirm(Member member, String orderId, int amount) throws Exception {
		return mockMvc.perform(post("/api/payments/confirm")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paymentKey\": \"pay-key\", \"orderId\": \"" + orderId + "\", \"amount\": " + amount + "}"));
	}

	private Rental reloadRental() {
		return rentalRepository.findById(rental.getId()).orElseThrow();
	}

	private Payment onlyPayment() {
		return paymentRepository.findAll().stream().reduce((first, second) -> {
			throw new AssertionError("결제가 2건 이상입니다");
		}).orElseThrow();
	}
}
