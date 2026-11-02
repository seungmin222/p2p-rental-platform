package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.dto.ItemDepositGuideRequest;
import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.support.IntegrationTestSupport;

class ItemDepositGuideIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private ItemQueryService queries;
	@Autowired
	private CategoryRepository categories;
	@Autowired
	private JdbcTemplate jdbc;

	@ParameterizedTest
	@CsvSource({"CAMPING,60000,100000", "TOOL,40000,60000", "CAMERA,100000,140000",
		"BABY,60000,100000", "PARTY,40000,60000", "HOBBY,60000,100000",
		"APPLIANCE,60000,60000", "ETC,60000,60000"})
	void calculatesCategoryRecommendationFromSeed(String code, int min, int max) {
		var result = queries.getDepositGuide(new ItemDepositGuideRequest(categoryId(code), 200000));
		assertThat(result.minDeposit()).isEqualTo(min);
		assertThat(result.maxDeposit()).isEqualTo(max);
	}

	@Test
	void supportsZeroPriceAndRoundsDownSubWonAmounts() {
		Long id = categoryId("CAMPING");
		var zero = queries.getDepositGuide(new ItemDepositGuideRequest(id, 0));
		assertThat(zero.minDeposit()).isZero();
		assertThat(zero.maxDeposit()).isZero();
		var fraction = queries.getDepositGuide(new ItemDepositGuideRequest(id, 1001));
		assertThat(fraction.minDeposit()).isEqualTo(300);
		assertThat(fraction.maxDeposit()).isEqualTo(500);
	}

	@Test
	void recommendationDoesNotClampToRegistrationDepositLimit() {
		var result = queries.getDepositGuide(new ItemDepositGuideRequest(categoryId("CAMERA"), 10000000));
		assertThat(result.minDeposit()).isEqualTo(5000000);
		assertThat(result.maxDeposit()).isEqualTo(7000000);
	}

	@Test
	void apiRequiresAuthenticationAndValidInput() throws Exception {
		var token = bearerToken(fixture.createMember().getId());
		var id = categoryId("CAMPING").toString();
		mockMvc.perform(get("/api/items/deposit-guide").param("categoryId", id).param("itemPrice", "200000"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
			.param("categoryId", id).param("itemPrice", "200000"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.minDeposit").value(60000))
			.andExpect(jsonPath("$.data.maxDeposit").value(100000));
		for (String price : new String[] {"-1", "10000001", "bad"}) {
			mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
				.param("categoryId", id).param("itemPrice", price)).andExpect(status().isBadRequest());
		}
		mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
			.param("categoryId", id)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
			.param("itemPrice", "200000")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
			.param("categoryId", Long.toString(Long.MAX_VALUE)).param("itemPrice", "200000"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("categoryId"));
	}

	@Test
	void inactiveCategoryIsRejected() throws Exception {
		var id = categoryId("CAMPING");
		var token = bearerToken(fixture.createMember().getId());
		jdbc.update("UPDATE categories SET is_active = FALSE WHERE id = ?", id);
		try {
			mockMvc.perform(get("/api/items/deposit-guide").header(HttpHeaders.AUTHORIZATION, token)
				.param("categoryId", id.toString()).param("itemPrice", "200000"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("categoryId"));
		} finally {
			jdbc.update("UPDATE categories SET is_active = TRUE WHERE id = ?", id);
		}
	}

	@Test
	void seedsFourGradesForEveryCategoryWithToolActualCostAndCameraCap() {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM damage_policies", Integer.class)).isEqualTo(32);
		assertThat(jdbc.queryForList("SELECT COUNT(*) FROM damage_policies GROUP BY category_id", Integer.class))
			.hasSize(8).allMatch(count -> count == 4);
		assertThat(
			jdbc.queryForObject("SELECT deduction_type FROM damage_policies WHERE category_id = ? AND grade = 'B'",
				String.class, categoryId("TOOL")))
			.isEqualTo("ACTUAL");
		assertThat(jdbc.queryForObject("SELECT cap_rate FROM damage_policies WHERE category_id = ? AND grade = 'C'",
			java.math.BigDecimal.class, categoryId("CAMERA"))).isEqualByComparingTo("0.70");
		assertThat(jdbc.queryForObject("SELECT fixed_amount FROM damage_policies WHERE category_id = ? AND grade = 'B'",
			Integer.class, categoryId("BABY"))).isEqualTo(20000);
	}

	private Long categoryId(String code) {
		return categories.findAll().stream().filter(c -> c.getCode().equals(code)).findFirst().orElseThrow().getId();
	}
}
