package com.team7.rental.member.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.team7.rental.support.IntegrationTestSupport;

/**
 * 회원·인증 API 계약(chore/member-item-api-contract). 서비스는 미구현이라 요청 검증(400)과 인증(401)만 확인한다.
 * 정상 흐름은 feat/member-auth·feat/member-region에서 검증한다.
 */
class MemberApiContractIntegrationTest extends IntegrationTestSupport {

	@Test
	@DisplayName("회원가입 형식 오류는 400 COMMON_INVALID_INPUT이고 필드별 details가 있다")
	void signupValidation() throws Exception {
		// given: 이메일 형식 오류, 비밀번호 특수문자 없음, 닉네임 1자
		String body = """
			{"email": "not-an-email", "password": "abcd1234", "nickname": "a"}
			""";

		// when, then
		mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("email", "password", "nickname")));
	}

	@Test
	@DisplayName("비밀번호 글자 수는 member.password 경계(7자 거부, 21자 거부)로 검사한다")
	void signupPasswordLength() throws Exception {
		for (String password : new String[] {"abc123!", "abcdefghij1234567890!"}) {
			String body = """
				{"email": "user@example.com", "password": "%s", "nickname": "동네주민"}
				""".formatted(password);

			mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[*].field", contains("password")));
		}
	}

	@Test
	@DisplayName("로그인 요청에 비밀번호가 없으면 400이다")
	void loginValidation() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"user@example.com\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("password")));
	}

	@Test
	@DisplayName("동네 인증 좌표가 범위를 벗어나면 400이다")
	void verifyCoordValidation() throws Exception {
		mockMvc.perform(patch("/api/members/me/regions/1/verify").header("Authorization", bearerToken(1L))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lat\": 91.0, \"lng\": 126.6}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("lat")));
	}

	@Test
	@DisplayName("회원 전용 API는 토큰 없이 부르면 401 AUTH_UNAUTHORIZED다")
	void memberOnlyRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/members/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
		mockMvc.perform(get("/api/members/me/regions"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/members/me/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}
}
