package com.team7.rental.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.batch.ExtensionPaymentExpireJob;
import com.team7.rental.payment.batch.PaymentReconcileJob;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.ExtensionStatus;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 연장료 결제 (REQ-038 + REQ-024 흐름). 대여 11/3 ~ 11/5 RENTING, 지금 11/5 20:00, 11/8까지 연장(3일, 30,000원).
 * 결제 대기는 payment.expire-minutes(15분) 동안 연장 기간을 선점한다.
 */
@TestPropertySource(properties = "app.feature.payment-enabled=true")
class ExtensionPaymentIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 20, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDate NEW_END = LocalDate.of(2026, 11, 8);
	private static final int FEE = 30_000;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalExtensionRepository extensionRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private ExtensionPaymentExpireJob expireJob;

	@Autowired
	private PaymentReconcileJob reconcileJob;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;
	private String token;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		token = bearerToken(renter.getId());
	}

	@Test
	@DisplayName("요청 → PENDING_PAYMENT, 주문 → 승인하면 연장 CONFIRMED, 종료일·횟수 갱신, EXTENSION 결제는 보증금 상태가 없고 양측 알림")
	void requestOrderConfirm() throws Exception {
		// when: 요청
		Long extensionId = requestExtension()
			.andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.data.additionalFee").value(FEE))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-11-05T20:15:00"))
			.andReturn().getResponse().getContentAsString().transform(this::extensionIdOf);

		// when: 주문
		Order order = createOrder(extensionId);

		// then
		assertThat(order.amount()).isEqualTo(FEE);

		// when: 승인
		confirm(order)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.type").value("EXTENSION"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.depositStatus").doesNotExist());

		// then
		Rental extended = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(extended.getEndDate()).isEqualTo(NEW_END);
		assertThat(extended.getExtensionCount()).isEqualTo(1);
		RentalExtension extension = extensionRepository.findById(extensionId).orElseThrow();
		assertThat(extension.getStatus()).isEqualTo(ExtensionStatus.CONFIRMED);
		Payment payment = paymentRepository.findById(extension.getPaymentId()).orElseThrow();
		assertThat(payment.getType()).isEqualTo(PaymentType.EXTENSION);
		assertThat(payment.getDepositStatus()).isNull();
		String eventId = "RENTAL_EXTENDED:" + rental.getId() + ":" + extensionId;
		assertThat(notificationPort.sent()).extracting(Sent::memberId, Sent::type, Sent::eventId)
			.containsExactlyInAnyOrder(tuple(renter.getId(), Type.RENTAL_EXTENDED, eventId),
				tuple(owner.getId(), Type.RENTAL_EXTENDED, eventId));
	}

	@Test
	@DisplayName("결제 대기 연장 기간은 예약·캘린더에서 점유되고, 만료되면(배치 전이라도) 다시 예약할 수 있다")
	void pendingExtensionOccupiesUntilExpiry() throws Exception {
		// given
		requestExtension();
		Member other = fixture.createVerifiedMember().member();

		// when & then: 결제 대기 중
		reserve(other, "2026-11-08", "2026-11-09")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_PERIOD_CONFLICT"));
		assertThat(calendarStatus("2026-11-06")).isEqualTo("BOOKED");
		assertThat(calendarStatus("2026-11-08")).isEqualTo("BOOKED");
		assertThat(calendarStatus("2026-11-09")).isEqualTo("AVAILABLE");

		// when & then: 15분 뒤(만료 배치는 아직)
		testClock.fixAt(NOW.plusMinutes(15));
		assertThat(calendarStatus("2026-11-08")).isEqualTo("AVAILABLE");
		reserve(other, "2026-11-08", "2026-11-09").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("결제 대기 연장이 있으면 두 번째 요청은 409 RENTAL_EXTENSION_IN_PROGRESS, 만료 뒤에는 다시 요청할 수 있다")
	void onePendingPerRental() throws Exception {
		requestExtension();

		extend("2026-11-07")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_EXTENSION_IN_PROGRESS"));

		testClock.fixAt(NOW.plusMinutes(15));
		extend("2026-11-07").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("금액이 다르면 400 PAYMENT_AMOUNT_MISMATCH와 함께 결제 FAILED, 연장 CANCELED(확정 실패라 바로 취소)")
	void amountMismatchCancelsExtension() throws Exception {
		// given
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order order = createOrder(extensionId);

		// when
		confirm(new Order(order.orderId(), FEE - 1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_AMOUNT_MISMATCH"));

		// then
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.CANCELED);
		assertThat(paymentOf(order).getStatus())
			.isEqualTo(PaymentStatus.FAILED);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getEndDate()).isEqualTo(END);
	}

	@Test
	@DisplayName("PG 오류는 결제만 FAILED이고 연장은 결제 대기로 남아, 15분 안에 새 주문으로 다시 결제할 수 있다(대여 결제와 같은 해석)")
	void pgFailureKeepsPending() throws Exception {
		// given
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order first = createOrder(extensionId);
		paymentGateway.failNextConfirm(Fault.ERROR);

		// when
		confirm(first).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_APPROVAL_FAILED"));

		// then
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.PENDING_PAYMENT);

		// when: 새 주문으로 재시도
		Order second = createOrder(extensionId);
		confirm(second).andExpect(status().isOk());

		// then
		assertThat(second.orderId()).isNotEqualTo(first.orderId());
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.CONFIRMED);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getEndDate()).isEqualTo(NEW_END);
		// 실패한 옛 주문은 연장에서 떨어져 다시 승인할 수 없다
		confirm(first).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_INVALID_STATUS"));
	}

	@Test
	@DisplayName("진행 중인 주문을 다시 요청하면 같은 주문이고, 다른 대여의 연장 ID는 404 RENTAL_EXTENSION_NOT_FOUND")
	void orderReuseAndOwnership() throws Exception {
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());

		assertThat(createOrder(extensionId)).isEqualTo(createOrder(extensionId));
		Rental otherRental = rentalFixture.createRental(item, renter, RentalStatus.RENTING,
			LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21));
		orderRequest(otherRental.getId(), extensionId)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_EXTENSION_NOT_FOUND"));
	}

	@Test
	@DisplayName("만료 배치: 15분 지나면 연장 CANCELED·READY 주문 FAILED, 만료 뒤 주문은 409, 배치를 다시 돌려도 그대로다")
	void expireBatch() throws Exception {
		// given
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order order = createOrder(extensionId);
		testClock.fixAt(NOW.plusMinutes(15));

		// when & then: 만료됐지만 배치 전
		orderRequest(rental.getId(), extensionId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_EXPIRED"));

		// when
		int expired = expireJob.expireDue(NOW.plusMinutes(15));
		int again = expireJob.expireDue(NOW.plusMinutes(16));

		// then
		assertThat(expired).isEqualTo(1);
		assertThat(again).isZero();
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.CANCELED);
		assertThat(paymentOf(order).getStatus())
			.isEqualTo(PaymentStatus.FAILED);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getEndDate()).isEqualTo(END);
		orderRequest(rental.getId(), extensionId).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
	}

	@Test
	@DisplayName("4-A: 결제 중 대여가 OVERDUE가 되면 승인 뒤 연장 CANCELED, 연장료 전액 환불, 409 PAYMENT_CANCELED_AFTER_EXPIRY, 종료일 그대로")
	void notRentingAtApprovalRefunds() throws Exception {
		// given
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order order = createOrder(extensionId);
		paymentGateway.beforeNextConfirm(() -> {
			Rental current = rentalRepository.findById(rental.getId()).orElseThrow();
			ReflectionTestUtils.setField(current, "status", RentalStatus.OVERDUE);
			rentalRepository.save(current);
		});

		// when
		confirm(order).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_CANCELED_AFTER_EXPIRY"));

		// then
		assertRefundedWithoutExtension(extensionId, order);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.OVERDUE);

		// when & then: 같은 주문 재호출은 PG를 다시 부르지 않는다
		confirm(order).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
		assertThat(paymentGateway.confirmCalls()).isEqualTo(1);
	}

	@Test
	@DisplayName("승인과 만료 배치가 동시에 돌아도 교착 없이 끝나고, 만료 시각이 지났으면 어느 쪽이 먼저든 연장 CANCELED + 전액 환불 1회다")
	void approvalRacesExpireBatch() throws Exception {
		// given: TX1은 만료 전에 통과하고, PG 호출 중에 만료 시각이 지난 뒤 TX2와 배치가 같이 출발한다
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order order = createOrder(extensionId);
		LocalDateTime afterExpiry = NOW.plusMinutes(16);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch go = new CountDownLatch(1);
		Future<Integer>[] batch = new Future[1];
		paymentGateway.beforeNextConfirm(() -> {
			testClock.fixAt(afterExpiry);
			batch[0] = executor.submit(() -> {
				go.await();
				return expireJob.expireDue(afterExpiry);
			});
			go.countDown();
		});

		try {
			// when
			ResultActions result = confirm(order);
			int expiredByBatch = batch[0].get(30, TimeUnit.SECONDS);

			// then
			result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("PAYMENT_CANCELED_AFTER_EXPIRY"));
			assertThat(expiredByBatch).isBetween(0, 1);
			assertRefundedWithoutExtension(extensionId, order);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("조정 배치: 결과를 모른 채 만료된 연장 주문이 PG에서는 승인됐으면, 만료 배치가 남겨 둔 주문을 승인으로 맞추고 전액 환불한다")
	void reconcileApprovedAfterExpiry() throws Exception {
		// given: PG 응답 없음 → 503, 연장은 결제 대기
		Long extensionId = extensionIdOf(requestExtension().andReturn().getResponse().getContentAsString());
		Order order = createOrder(extensionId);
		paymentGateway.failNextConfirm(Fault.NOT_APPLIED);
		confirm(order).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error.code").value("PAYMENT_RESULT_UNKNOWN"));
		paymentGateway.seedApproved("pay-" + order.orderId(), order.orderId(), FEE);

		// when: 만료 배치는 연장만 취소하고, 승인 결과를 모르는 주문은 조정 배치에 남긴다
		LocalDateTime afterExpiry = NOW.plusMinutes(16);
		testClock.fixAt(afterExpiry);
		expireJob.expireDue(afterExpiry);

		// then
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.CANCELED);
		assertThat(paymentOf(order).getStatus()).isEqualTo(PaymentStatus.READY);

		// when
		reconcileJob.reconcileRecent(afterExpiry);

		// then
		assertRefundedWithoutExtension(extensionId, order);
	}

	private void assertRefundedWithoutExtension(Long extensionId, Order order) {
		assertThat(extensionRepository.findById(extensionId).orElseThrow().getStatus())
			.isEqualTo(ExtensionStatus.CANCELED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(FEE);
		Payment payment = paymentOf(order);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(payment.getDepositStatus()).isNull();
		Rental current = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(current.getEndDate()).isEqualTo(END);
		assertThat(current.getExtensionCount()).isZero();
		assertThat(notificationPort.sent()).noneMatch(sent -> sent.type() == Type.RENTAL_EXTENDED);
	}

	private Payment paymentOf(Order order) {
		return paymentRepository.findAll().stream()
			.filter(payment -> payment.getOrderId().equals(order.orderId()))
			.findFirst().orElseThrow();
	}

	private record Order(String orderId, int amount) {
	}

	private ResultActions requestExtension() throws Exception {
		return extend(NEW_END.toString()).andExpect(status().isCreated());
	}

	private ResultActions extend(String newEndDate) throws Exception {
		return mockMvc.perform(post("/api/rentals/{id}/extensions", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"newEndDate\": \"" + newEndDate + "\"}"));
	}

	private Long extensionIdOf(String body) {
		return ((Number)JsonPath.read(body, "$.data.extensionId")).longValue();
	}

	private ResultActions orderRequest(Long rentalId, Long extensionId) throws Exception {
		return mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"rentalId\": " + rentalId + ", \"extensionId\": " + extensionId + "}"));
	}

	private Order createOrder(Long extensionId) throws Exception {
		String body = orderRequest(rental.getId(), extensionId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.orderName").value(item.getName() + " 대여 연장"))
			.andReturn().getResponse().getContentAsString();
		return new Order(JsonPath.read(body, "$.data.orderId"), JsonPath.read(body, "$.data.amount"));
	}

	private ResultActions confirm(Order order) throws Exception {
		return mockMvc.perform(post("/api/payments/confirm").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paymentKey\": \"pay-" + order.orderId() + "\", \"orderId\": \"" + order.orderId()
				+ "\", \"amount\": " + order.amount() + "}"));
	}

	private ResultActions reserve(Member member, String start, String end) throws Exception {
		return mockMvc.perform(post("/api/rentals").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"itemId\": " + item.getId() + ", \"startDate\": \"" + start + "\", \"endDate\": \"" + end
				+ "\"}"));
	}

	private String calendarStatus(String date) throws Exception {
		String body = mockMvc.perform(get("/api/items/{id}/calendar", item.getId()).param("month", "2026-11"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<String> statuses = JsonPath.read(body, "$.data.days[?(@.date == '" + date + "')].status");
		return statuses.getFirst();
	}
}
