package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

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
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 연체료 확정과 보증금 차감 (REQ-023 → REQ-025). 일 대여료 10,000원(연체 1일 15,000원), 보증금 50,000원, 반납 예정일 11-05.
 */
class OverdueReturnIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	private Member owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("연체 2일에 반납 요청하면 30,000원이 확정되고, 정상 판정 시 보증금에서 빼고 20,000원만 반환한다")
	void overdueFeeConfirmedAndDeducted() throws Exception {
		// given
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		paymentFixture.createApproved(rental);
		testClock.fixAt(END.plusDays(2).atTime(10, 0));

		// when, then: 반납 전 상세는 오늘 기준 현재까지 연체료
		detail(rental, renter).andExpect(status().isOk()).andExpect(jsonPath("$.data.overdueFee").value(30_000));

		requestReturn(rental).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.overdueFee").value(30_000));
		assertThat(reload(rental).getOverdueFee()).isEqualTo(30_000);

		// 반납 뒤 날짜가 지나도 확정값 그대로
		testClock.fixAt(END.plusDays(5).atTime(10, 0));
		detail(rental, owner).andExpect(jsonPath("$.data.overdueFee").value(30_000));

		confirm(rental).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
		assertThat(paymentGateway.appliedCancels()).containsExactly(20_000);
	}

	@Test
	@DisplayName("연체료가 보증금 이상이면 보증금이 상한이고, 판정 시 PG 호출 없이 FORFEITED·COMPLETED다")
	void cappedAndForfeited() throws Exception {
		// given: 4일 × 15,000 = 60,000 > 50,000
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		Payment payment = paymentFixture.createApproved(rental);
		testClock.fixAt(END.plusDays(4).atTime(10, 0));

		// when
		requestReturn(rental).andExpect(status().isOk()).andExpect(jsonPath("$.data.overdueFee").value(50_000));
		confirm(rental).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		// then
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getDepositStatus())
			.isEqualTo(DepositStatus.FORFEITED);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("연체 배치 전(RENTING)이어도 예정일이 지났으면 상세·반납 연체료를 날짜로 계산한다")
	void rentingPastEndBeforeBatch() throws Exception {
		// given: 00:03, 배치(00:05) 전
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		testClock.fixAt(END.plusDays(1).atTime(0, 3));

		// when, then
		detail(rental, renter).andExpect(jsonPath("$.data.status").value("RENTING"))
			.andExpect(jsonPath("$.data.overdueFee").value(15_000));
		requestReturn(rental).andExpect(status().isOk());
		assertThat(reload(rental).getOverdueFee()).isEqualTo(15_000);
	}

	@Test
	@DisplayName("예정일 당일 반납은 연체료 0원이다")
	void returnOnEndDate() throws Exception {
		// given
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		testClock.fixAt(END.atTime(23, 59));

		// when, then
		detail(rental, renter).andExpect(jsonPath("$.data.overdueFee").value(0));
		requestReturn(rental).andExpect(status().isOk());
		assertThat(reload(rental).getOverdueFee()).isZero();
	}

	@Test
	@DisplayName("보증금 0원 대여는 연체해도 연체료 0원으로 저장한다(policies.md 무료 대여)")
	void zeroDepositNotCharged() throws Exception {
		// given
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		ReflectionTestUtils.setField(rental, "deposit", 0);
		rental = rentalRepository.save(rental);
		testClock.fixAt(END.plusDays(3).atTime(10, 0));

		// when, then
		detail(rental, renter).andExpect(jsonPath("$.data.overdueFee").value(0));
		requestReturn(rental).andExpect(status().isOk());
		assertThat(reload(rental).getOverdueFee()).isZero();
	}

	private ResultActions detail(Rental rental, Member member) throws Exception {
		return mockMvc.perform(get("/api/rentals/{rentalId}", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions requestReturn(Rental rental) throws Exception {
		return multipartAs(HttpMethod.POST, "/api/rentals/{rentalId}/return-request", rental, renter, json(ALL_TRUE),
			image());
	}

	private ResultActions confirm(Rental rental) throws Exception {
		return multipartAs(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", rental, owner, json(NORMAL));
	}

	private ResultActions multipartAs(HttpMethod method, String path, Rental rental, Member member,
		MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(method, path, rental.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private Rental reload(Rental rental) {
		return rentalRepository.findById(rental.getId()).orElseThrow();
	}
}
