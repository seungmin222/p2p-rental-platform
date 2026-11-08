package com.team7.rental.payment.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 결제·보증금 내역 (REQ-033, GET /api/members/me/payments). type은 open-issues #44 임시 해석. 오늘은 2026-11-10이고 기본 기간은
 * 2026-08-10 ~ 2026-11-10이다. 결제는 대여료 30,000원 + 보증금 50,000원.
 */
class MyPaymentIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 9, 0);

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RentalRepository rentalRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private int nextStart;

	private Payment frozen;
	private Payment depositReturned;
	private Payment cancelRefunded;
	private Payment held;
	private Payment old;

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).item();

		old = approvedAt(LocalDateTime.of(2026, 8, 9, 23, 59), RentalStatus.COMPLETED);
		frozen = approvedAt(LocalDateTime.of(2026, 8, 10, 0, 0), RentalStatus.RESERVED);
		depositReturned = approvedAt(LocalDateTime.of(2026, 10, 1, 10, 0), RentalStatus.COMPLETED);
		depositReturned.requestRefund(35_000, NOW);
		depositReturned.applyRefund();
		paymentRepository.save(depositReturned);
		setOverdueFee(depositReturned, 15_000);
		cancelRefunded = approvedAt(LocalDateTime.of(2026, 10, 20, 10, 0), RentalStatus.CANCELED);
		cancelRefunded.requestRefund(80_000, NOW);
		cancelRefunded.applyRefund();
		paymentRepository.save(cancelRefunded);
		held = approvedAt(LocalDateTime.of(2026, 11, 10, 23, 59), RentalStatus.RETURNED);
		held.holdDeposit(NOW);
		held.recordDeduction(DamageGrade.C, 20_000, "렌즈 흠집 수리비");
		paymentRepository.save(held);

		// 승인되지 않은 주문(READY·FAILED)은 내역에 없다
		createdAt(LocalDateTime.of(2026, 11, 1, 10, 0));
		paymentRepository.save(Payment.createForRental(rental(RentalStatus.CANCELED).getId(), "order-ready",
			"key-ready", 30_000, 50_000));
		Payment failed = Payment.createForRental(rental(RentalStatus.CANCELED).getId(), "order-failed",
			"key-failed", 30_000, 50_000);
		failed.fail();
		paymentRepository.save(failed);

		// 다른 회원이 빌린 결제는 보이지 않는다
		Member otherRenter = fixture.createVerifiedMember().member();
		createdAt(LocalDateTime.of(2026, 11, 2, 10, 0));
		paymentFixture.createApproved(rentalFixture.createRental(item, otherRenter, RentalStatus.RESERVED,
			LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 21)));

		testClock.fixAt(NOW);
	}

	@Test
	@DisplayName("기본은 최근 3개월(from·to 포함)의 승인된 본인 결제 전체, 최신 순이고 금액·상태·연체료·차감이 함께 온다")
	void defaultAll() throws Exception {
		get(null, null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(4))
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(held), id(cancelRefunded),
				id(depositReturned), id(frozen))))
			.andExpect(jsonPath("$.data.items[0].rentalId").value(held.getRentalId()))
			.andExpect(jsonPath("$.data.items[0].item.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.items[0].paymentType").value("RENTAL"))
			.andExpect(jsonPath("$.data.items[0].amount").value(80_000))
			.andExpect(jsonPath("$.data.items[0].rentalFee").value(30_000))
			.andExpect(jsonPath("$.data.items[0].deposit").value(50_000))
			.andExpect(jsonPath("$.data.items[0].extensionFee").value(0))
			.andExpect(jsonPath("$.data.items[0].depositStatus").value("HELD"))
			.andExpect(jsonPath("$.data.items[0].deduction.grade").value("C"))
			.andExpect(jsonPath("$.data.items[0].deduction.amount").value(20_000))
			.andExpect(jsonPath("$.data.items[0].deduction.reason").value("렌즈 흠집 수리비"))
			.andExpect(jsonPath("$.data.items[2].paymentStatus").value("PARTIAL_CANCELED"))
			.andExpect(jsonPath("$.data.items[2].depositStatus").value("REFUNDED"))
			.andExpect(jsonPath("$.data.items[2].refundedAmount").value(35_000))
			.andExpect(jsonPath("$.data.items[2].overdueFee").value(15_000))
			.andExpect(jsonPath("$.data.items[2].deduction").doesNotExist())
			.andExpect(jsonPath("$.data.items[3].depositStatus").value("FROZEN"));
		get("PAYMENT", null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(4));
	}

	@Test
	@DisplayName("REFUND는 취소 환불이 있는 건, DEPOSIT은 보증금이 동결 이후 단계로 넘어간 건이다")
	void typeFilters() throws Exception {
		get("REFUND", null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(cancelRefunded))))
			.andExpect(jsonPath("$.data.items[0].paymentStatus").value("CANCELED"));
		get("DEPOSIT", null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(held), id(cancelRefunded),
				id(depositReturned))));
	}

	@Test
	@DisplayName("from·to를 주면 그 날짜 범위(둘 다 포함)로 거른다. from만 주면 to는 오늘이다")
	void period() throws Exception {
		get(null, "2026-08-09", "2026-08-10").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(frozen), id(old))));
		get(null, "2026-10-01", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(held), id(cancelRefunded),
				id(depositReturned))));
		get(null, null, "2026-10-01").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].paymentId").value(Matchers.contains(id(depositReturned),
				id(frozen), id(old))));
	}

	@Test
	@DisplayName("제공자가 부르면 자기 물품을 빌려 간 대여자의 결제는 보이지 않는다(제공자 쪽은 정산 내역)")
	void ownerSeesNoRenterPayments() throws Exception {
		for (String type : new String[] {"ALL", "PAYMENT", "REFUND", "DEPOSIT"}) {
			get(owner, type, "2026-01-01", "2026-12-31").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.items").isEmpty());
		}
	}

	@Test
	@DisplayName("from > to면 400(기본값을 채운 뒤 기준), type이 틀리면 400, 로그인하지 않으면 401")
	void invalid() throws Exception {
		get(null, "2026-10-02", "2026-10-01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("to"));
		get(null, "2026-11-11", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		get("UNKNOWN", null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(MockMvcRequestBuilders.get("/api/members/me/payments"))
			.andExpect(status().isUnauthorized());
	}

	private Payment approvedAt(LocalDateTime createdAt, RentalStatus status) {
		createdAt(createdAt);
		return paymentFixture.createApproved(rental(status));
	}

	private Rental rental(RentalStatus status) {
		LocalDate start = LocalDate.of(2026, 1, 1).plusDays(nextStart);
		nextStart += 3;
		return rentalFixture.createRental(item, renter, status, start, start.plusDays(2));
	}

	private void createdAt(LocalDateTime at) {
		testClock.fixAt(at);
	}

	private void setOverdueFee(Payment payment, int overdueFee) {
		Rental rental = rentalRepository.findById(payment.getRentalId()).orElseThrow();
		ReflectionTestUtils.setField(rental, "overdueFee", overdueFee);
		rentalRepository.save(rental);
	}

	private static int id(Payment payment) {
		return payment.getId().intValue();
	}

	private ResultActions get(String type, String from, String to) throws Exception {
		return get(renter, type, from, to);
	}

	private ResultActions get(Member member, String type, String from, String to) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get("/api/members/me/payments")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()));
		if (type != null) {
			request.param("type", type);
		}
		if (from != null) {
			request.param("from", from);
		}
		if (to != null) {
			request.param("to", to);
		}
		return mockMvc.perform(request);
	}
}
