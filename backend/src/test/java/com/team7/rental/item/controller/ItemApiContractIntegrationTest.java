package com.team7.rental.item.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.support.IntegrationTestSupport;

/**
 * 물품 API 계약(chore/member-item-api-contract). 서비스는 미구현이라 요청 검증(400)과 인증(401)만 확인한다.
 * 정상 흐름은 feat/item-crud·feat/item-feed-search에서 검증한다.
 */
class ItemApiContractIntegrationTest extends IntegrationTestSupport {

	@Test
	@DisplayName("물품 등록 request part 검증 실패는 400이고 details에 위반 필드가 모두 있다")
	void createValidation() throws Exception {
		// given: 오늘 2026-11-01. 이름 51자, 일 대여료 500원(1~999원 불가), 보증금 상한 초과, 시작일이 어제, 기간 겹침
		testClock.fixAt(LocalDateTime.of(2026, 11, 1, 10, 0));
		String body = """
			{"name": "%s", "description": "설명", "categoryId": 1, "condition": "USED",
			 "pricePerDay": 500, "itemPrice": 0, "deposit": 3000001,
			 "availablePeriods": [{"startDate": "2026-10-31", "endDate": "2026-11-05"},
			                      {"startDate": "2026-11-05", "endDate": "2026-11-10"}],
			 "address": "인천 연수구 송도동", "latitude": 37.39, "longitude": 126.63}
			""".formatted("가".repeat(51));

		// when, then
		mockMvc.perform(multipart("/api/items").file(json(body)).file(image())
			.header("Authorization", bearerToken(1L)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[*].field",
				hasItems("name", "pricePerDay", "deposit", "availablePeriods")))
			.andExpect(jsonPath("$.error.details[?(@.field == 'availablePeriods')]", hasSize(2)));
	}

	@Test
	@DisplayName("물품 등록 필수 필드가 빠지면 400이다")
	void createRequiredFields() throws Exception {
		mockMvc.perform(multipart("/api/items").file(json("{}")).file(image())
			.header("Authorization", bearerToken(1L)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", hasItems("name", "description", "categoryId",
				"condition", "pricePerDay", "itemPrice", "deposit", "availablePeriods", "address", "latitude",
				"longitude")));
	}

	@Test
	@DisplayName("물품 수정 imageOrder 칸에 imageId와 newImageIndex를 함께 넣으면 400이다")
	void updateImageOrderValidation() throws Exception {
		mockMvc
			.perform(multipart("/api/items/1").file(json("{\"imageOrder\": [{\"imageId\": 1, \"newImageIndex\": 0}]}"))
				.with(request -> {
					request.setMethod("PATCH");
					return request;
				})
				.header("Authorization", bearerToken(1L)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("imageOrder")));
	}

	@Test
	@DisplayName("검색 시작일이 종료일보다 늦으면 400이다")
	void searchPeriodValidation() throws Exception {
		mockMvc.perform(get("/api/items").param("startDate", "2026-11-05").param("endDate", "2026-11-03"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("endDate")));
	}

	@Test
	@DisplayName("검색 최소 금액이 최대 금액보다 크면 400이다")
	void searchPriceValidation() throws Exception {
		mockMvc.perform(get("/api/items").param("minPrice", "20000").param("maxPrice", "10000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("minPrice")));
	}

	@Test
	@DisplayName("물품 회원 전용 API는 토큰 없이 부르면 401이다")
	void memberOnlyRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/items/deposit-guide").param("categoryId", "1").param("itemPrice", "100000"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
		mockMvc.perform(delete("/api/items/1"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/members/me/items"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/members/me/items/1"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/items/1/rentals"))
			.andExpect(status().isUnauthorized());
	}
}
