package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** GET /api/items/{itemId}/calendar (REQ-012). 오늘은 10/28, 가능 기간은 [10/25, 11/10] */
class RentalCalendarIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 10)).item();
	}

	@Test
	@DisplayName("month를 생략하면 이번 달이고, 지난 날짜·가능 기간 밖은 UNAVAILABLE, 오늘부터 AVAILABLE이다")
	void defaultMonth() throws Exception {
		// when
		Map<String, String> days = calendar(null, null);

		// then
		assertThat(days).hasSize(31);
		assertThat(days.get("2026-10-01")).isEqualTo("UNAVAILABLE"); // 가능 기간 밖
		assertThat(days.get("2026-10-27")).isEqualTo("UNAVAILABLE"); // 가능 기간 안이지만 지난 날짜
		assertThat(days.get("2026-10-28")).isEqualTo("AVAILABLE"); // 오늘
		assertThat(days.get("2026-10-31")).isEqualTo("AVAILABLE");
	}

	@Test
	@DisplayName("달을 넘는 대여는 두 달 모두에서 BOOKED이고, 경계 다음 날은 AVAILABLE이다")
	void bookedAcrossMonths() throws Exception {
		// given: [10/30, 11/2]
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, LocalDate.of(2026, 10, 30),
			LocalDate.of(2026, 11, 2));

		// when
		Map<String, String> october = calendar("2026-10", null);
		Map<String, String> november = calendar("2026-11", null);

		// then
		assertThat(october.get("2026-10-29")).isEqualTo("AVAILABLE");
		assertThat(october.get("2026-10-30")).isEqualTo("BOOKED");
		assertThat(october.get("2026-10-31")).isEqualTo("BOOKED");
		assertThat(november).hasSize(30);
		assertThat(november.get("2026-11-01")).isEqualTo("BOOKED");
		assertThat(november.get("2026-11-02")).isEqualTo("BOOKED");
		assertThat(november.get("2026-11-03")).isEqualTo("AVAILABLE");
		assertThat(november.get("2026-11-10")).isEqualTo("AVAILABLE");
		assertThat(november.get("2026-11-11")).isEqualTo("UNAVAILABLE");
	}

	@Test
	@DisplayName("취소된 대여와 만료된 결제 대기는 BOOKED가 아니고, 만료 전 결제 대기는 BOOKED다")
	void occupancyRules() throws Exception {
		// given
		rentalFixture.createRental(item, renter, RentalStatus.CANCELED, LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 1));
		rentalFixture.createPendingPayment(item, renter, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), NOW);
		rentalFixture.createPendingPayment(item, renter, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5),
			NOW.plusMinutes(1));

		// when
		Map<String, String> november = calendar("2026-11", null);

		// then
		assertThat(november.get("2026-11-01")).isEqualTo("AVAILABLE");
		assertThat(november.get("2026-11-03")).isEqualTo("AVAILABLE");
		assertThat(november.get("2026-11-05")).isEqualTo("BOOKED");
	}

	@Test
	@DisplayName("month 형식이 틀리면 400 COMMON_INVALID_INPUT이다")
	void invalidMonth() throws Exception {
		mockMvc.perform(get("/api/items/{itemId}/calendar", item.getId()).param("month", "2026-13"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(get("/api/items/{itemId}/calendar", item.getId()).param("month", "2026/10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("없는 물품·삭제된 물품은 404, 숨김 물품은 소유자만 조회하고 그 외는 404다")
	void visibility() throws Exception {
		// given
		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", item.getId());

		// when, then
		expectNotFound(item.getId(), null);
		expectNotFound(item.getId(), bearerToken(renter.getId()));
		mockMvc.perform(get("/api/items/{itemId}/calendar", item.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.member().getId())))
			.andExpect(status().isOk());

		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());
		expectNotFound(item.getId(), bearerToken(owner.member().getId()));
		expectNotFound(item.getId() + 999, null);
	}

	@Test
	@DisplayName("비회원 허용 경로여도 토큰이 무효하면 401 AUTH_UNAUTHORIZED다")
	void invalidToken() throws Exception {
		mockMvc.perform(get("/api/items/{itemId}/calendar", item.getId())
			.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	private void expectNotFound(Long itemId, String authorization) throws Exception {
		var request = get("/api/items/{itemId}/calendar", itemId);
		if (authorization != null) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		mockMvc.perform(request)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
	}

	/** 날짜 → 상태 (비회원 조회) */
	private Map<String, String> calendar(String month, String authorization) throws Exception {
		var request = get("/api/items/{itemId}/calendar", item.getId());
		if (month != null) {
			request.param("month", month);
		}
		if (authorization != null) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		MvcResult result = mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.month").value(month != null ? month : "2026-10"))
			.andReturn();
		String json = result.getResponse().getContentAsString();
		List<String> dates = JsonPath.read(json, "$.data.days[*].date");
		List<String> statuses = JsonPath.read(json, "$.data.days[*].status");
		Map<String, String> days = new LinkedHashMap<>();
		for (int i = 0; i < dates.size(); i++) {
			days.put(dates.get(i), statuses.get(i));
		}
		return days;
	}
}
