package com.team7.rental.payment.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 결제를 켠 예약 → 주문 → 승인 → 취소 환불 흐름. 결제 테스트는 대부분 PENDING_PAYMENT 대여를 바로 저장해 플래그가 필요 없고, 플래그가 예약에 주는 영향만 여기서 본다.
 */
@TestPropertySource(properties = "app.feature.payment-enabled=true")
class PaymentEnabledFlowIntegrationTest extends IntegrationTestSupport {

	@Test
	@DisplayName("예약이 PENDING_PAYMENT로 만들어지고, 결제하면 RESERVED, 취소하면 전액 환불된다")
	void reserveConfirmCancel() throws Exception {
		// given
		testClock.fixAt(LocalDateTime.of(2026, 10, 28, 10, 0));
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		String token = bearerToken(renter.getId());

		// when: 예약
		String created = mockMvc.perform(post("/api/rentals").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"itemId\": " + item.getId() + ", \"startDate\": \"2026-11-03\", \"endDate\": \"2026-11-05\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
			.andReturn().getResponse().getContentAsString();
		Number rentalId = JsonPath.read(created, "$.data.rentalId");

		// when: 주문
		String order = mockMvc.perform(post("/api/payments").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"rentalId\": " + rentalId + "}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String orderId = JsonPath.read(order, "$.data.orderId");
		int amount = JsonPath.read(order, "$.data.amount");

		// when: 승인
		mockMvc.perform(post("/api/payments/confirm").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paymentKey\": \"pay\", \"orderId\": \"" + orderId + "\", \"amount\": " + amount + "}"))
			.andExpect(status().isOk());

		// then
		mockMvc.perform(get("/api/rentals/{id}", rentalId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.data.status").value("RESERVED"));

		// when: 취소
		mockMvc.perform(patch("/api/rentals/{id}/cancel", rentalId).header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"SCHEDULE_CHANGE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));

		// then
		assertThat(paymentGateway.appliedCancels()).containsExactly(amount);
	}
}
