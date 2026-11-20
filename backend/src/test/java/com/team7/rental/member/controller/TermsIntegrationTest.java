package com.team7.rental.member.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.member.service.TermsProperties;
import com.team7.rental.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 약관 동의 및 정책 안내 (REQ-037) */
class TermsIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "terms@example.com";
	private static final String PASSWORD = "rental123!";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TermsProperties termsProperties;

	@Test
	@DisplayName("가입: 세 항목 모두 동의하면 201이고 각 항목의 현재 버전으로 3행을 남긴다. 로그인 응답 requiresTermsAgreement는 false")
	void signupRecordsAgreements() throws Exception {
		Long memberId = signup();

		assertThat(jdbcTemplate.queryForList(
			"SELECT CONCAT(terms_type, ':', terms_version) FROM terms_agreements WHERE member_id = ?", String.class,
			memberId)).containsExactlyInAnyOrder("TERMS_OF_SERVICE:" + termsProperties.termsOfServiceVersion(),
				"PRIVACY_COLLECTION:" + termsProperties.privacyPolicyVersion(),
				"AGE_OVER_14:" + termsProperties.ageOver14Version());
		login().andExpect(jsonPath("$.data.requiresTermsAgreement").value(false));
	}

	@Test
	@DisplayName("가입: 하나라도 false면 400 details agreements.{필드}, agreements를 빼면 details agreements. 회원은 만들어지지 않는다")
	void signupRequiresAgreements() throws Exception {
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
			.content(signupBody("true", "true", "false")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[*].field", contains("agreements.ageOver14")));
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"동네주민\", \"agreements\": {}}"
				.formatted(EMAIL, PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("agreements.termsOfService",
				"agreements.privacyCollection", "agreements.ageOver14")));
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\", \"nickname\": \"동네주민\"}".formatted(EMAIL, PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("agreements")));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members", Integer.class)).isZero();
	}

	@Test
	@DisplayName("GET /api/terms: 비회원도 200, 이용약관·개인정보처리방침·대여 금지 품목 순서로 버전과 마크다운 본문을 준다")
	void documents() throws Exception {
		mockMvc.perform(get("/api/terms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].type").value("TERMS_OF_SERVICE"))
			.andExpect(jsonPath("$.data[0].title").value("이용약관"))
			.andExpect(jsonPath("$.data[0].version").value(termsProperties.termsOfServiceVersion()))
			.andExpect(jsonPath("$.data[0].content", containsString("# 이용약관")))
			.andExpect(jsonPath("$.data[1].type").value("PRIVACY_POLICY"))
			.andExpect(jsonPath("$.data[1].version").value(termsProperties.privacyPolicyVersion()))
			.andExpect(jsonPath("$.data[1].content", containsString("# 개인정보처리방침")))
			.andExpect(jsonPath("$.data[2].type").value("PROHIBITED_ITEMS"))
			.andExpect(jsonPath("$.data[2].version").value(termsProperties.prohibitedItemsVersion()))
			.andExpect(jsonPath("$.data[2].content", containsString("의약품·의료기기")));
	}

	@Test
	@DisplayName("개정: 동의한 버전이 현재와 다르면 로그인·재발급 응답이 true, 일부만 재동의하면 여전히 true, 모두 재동의하면 false")
	void reagreeAfterRevision() throws Exception {
		Long memberId = signup();
		jdbcTemplate.update("UPDATE terms_agreements SET terms_version = 'old' WHERE member_id = ? AND terms_type IN "
			+ "('TERMS_OF_SERVICE', 'PRIVACY_COLLECTION')", memberId);

		MvcResult loggedIn = login().andExpect(jsonPath("$.data.requiresTermsAgreement").value(true)).andReturn();
		String token = "Bearer " + JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.data.accessToken");
		Cookie refresh = loggedIn.getResponse().getCookie("refreshToken");
		mockMvc.perform(post("/api/auth/reissue").cookie(refresh))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresTermsAgreement").value(true));

		reagree(token, "[\"TERMS_OF_SERVICE\"]").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresTermsAgreement").value(true));
		reagree(token, "[\"TERMS_OF_SERVICE\", \"PRIVACY_COLLECTION\", \"AGE_OVER_14\"]").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresTermsAgreement").value(false));

		// 옛 버전 행은 남고, 현재 버전 행이 하나씩 더해진다(이미 동의한 AGE_OVER_14·TERMS_OF_SERVICE는 다시 넣지 않는다)
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM terms_agreements WHERE member_id = ?",
			Integer.class, memberId)).isEqualTo(5);
		login().andExpect(jsonPath("$.data.requiresTermsAgreement").value(false));
	}

	@Test
	@DisplayName("동의 기록이 없는 회원(037 이전 가입)은 로그인 응답이 true다")
	void memberWithoutAgreements() throws Exception {
		Long memberId = signup();
		jdbcTemplate.update("DELETE FROM terms_agreements WHERE member_id = ?", memberId);

		login().andExpect(jsonPath("$.data.requiresTermsAgreement").value(true));
	}

	@Test
	@DisplayName("재동의: 토큰 없으면 401, 모르는 항목·빈 목록이면 400")
	void reagreeValidation() throws Exception {
		Long memberId = signup();
		String token = bearerToken(memberId);

		mockMvc.perform(post("/api/members/me/terms-agreements").contentType(MediaType.APPLICATION_JSON)
			.content("{\"types\": [\"TERMS_OF_SERVICE\"]}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
		reagree(token, "[\"UNKNOWN\"]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		reagree(token, "[]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("types")));
	}

	private Long signup() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
			.content(signupBody("true", "true", "true")))
			.andExpect(status().isCreated())
			.andReturn();
		return ((Number)JsonPath.read(result.getResponse().getContentAsString(), "$.data.memberId")).longValue();
	}

	private ResultActions login() throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD)))
			.andExpect(status().isOk());
	}

	private ResultActions reagree(String token, String types) throws Exception {
		return mockMvc.perform(post("/api/members/me/terms-agreements")
			.header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"types\": " + types + "}"));
	}

	private static String signupBody(String termsOfService, String privacyCollection, String ageOver14) {
		return """
			{"email": "%s", "password": "%s", "nickname": "동네주민",
			 "agreements": {"termsOfService": %s, "privacyCollection": %s, "ageOver14": %s}}
			""".formatted(EMAIL, PASSWORD, termsOfService, privacyCollection, ageOver14);
	}
}
