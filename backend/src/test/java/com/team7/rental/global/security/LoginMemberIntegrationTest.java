package com.team7.rental.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 실제 SecurityConfig·필터·리졸버·springdoc을 모두 띄워 @LoginMember를 확인한다.
 * 테스트 컨트롤러는 실제 API와 겹치지 않는 고정 경로를 쓴다.
 * OPTIONAL은 GET /api/items/* 공개 패턴(비회원 허용), REQUIRED는 나머지(anyRequest, 회원 전용) 규칙에 걸린다.
 */
@Import(LoginMemberIntegrationTest.TestController.class)
@TestPropertySource(properties = "app.security.permit-swagger=true")
class LoginMemberIntegrationTest extends IntegrationTestSupport {

	private static final String OPTIONAL = "/api/items/login-member-it";
	private static final String REQUIRED = "/api/login-member-it";

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("유효한 Access Token이면 memberId가 주입된다")
	void validToken() throws Exception {
		// given
		String token = jwtProvider.createAccessToken(7L, "USER");

		// when, then
		mockMvc.perform(get(REQUIRED).header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(7));
		mockMvc.perform(get(OPTIONAL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").value(7));
	}

	@Test
	@DisplayName("required=false인 경로에 헤더가 없으면 비회원으로 null이 주입된다")
	void optionalWithoutHeader() throws Exception {
		mockMvc.perform(get(OPTIONAL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("회원 전용 경로에 헤더가 없으면 401 AUTH_UNAUTHORIZED다")
	void requiredWithoutHeader() throws Exception {
		expectUnauthorized(mockMvc.perform(get(REQUIRED)));
	}

	@Test
	@DisplayName("헤더가 무효하면 비회원 허용 경로여도 401 AUTH_UNAUTHORIZED다")
	void invalidToken() throws Exception {
		expectUnauthorized(mockMvc.perform(get(OPTIONAL).header(HttpHeaders.AUTHORIZATION, bearer("invalid.token"))));
		expectUnauthorized(mockMvc.perform(get(OPTIONAL).header(HttpHeaders.AUTHORIZATION, "Basic abc")));
		expectUnauthorized(mockMvc.perform(get(REQUIRED).header(HttpHeaders.AUTHORIZATION, bearer("invalid.token"))));
	}

	@Test
	@DisplayName("만료된 토큰이면 비회원 허용 경로여도 401 AUTH_UNAUTHORIZED다")
	void expiredToken() throws Exception {
		// given: 1시간 전에 발급한 AT (유효 30분)
		testClock.fixAt(LocalDateTime.now(testClock).minusHours(1));
		String expired = jwtProvider.createAccessToken(7L, "USER");
		testClock.reset();

		// when, then
		expectUnauthorized(mockMvc.perform(get(OPTIONAL).header(HttpHeaders.AUTHORIZATION, bearer(expired))));
		expectUnauthorized(mockMvc.perform(get(REQUIRED).header(HttpHeaders.AUTHORIZATION, bearer(expired))));
	}

	@Test
	@DisplayName("/v3/api-docs에 @LoginMember 파라미터가 노출되지 않는다")
	void loginMemberHiddenInApiDocs() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['" + REQUIRED + "'].get").exists())
			.andExpect(jsonPath("$.paths['" + REQUIRED + "'].get.parameters").doesNotExist())
			.andExpect(jsonPath("$.paths['" + OPTIONAL + "'].get").exists())
			.andExpect(jsonPath("$.paths['" + OPTIONAL + "'].get.parameters").doesNotExist());
	}

	private void expectUnauthorized(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))
			.andExpect(jsonPath("$.error.message").isNotEmpty())
			.andExpect(jsonPath("$.error.details").isArray());
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	@RestController
	static class TestController {

		@GetMapping(OPTIONAL)
		ApiResponse<Long> optional(@LoginMember(required = false)
		Long memberId) {
			return ApiResponse.ok(memberId);
		}

		@GetMapping(REQUIRED)
		ApiResponse<Long> required(@LoginMember
		Long memberId) {
			return ApiResponse.ok(memberId);
		}
	}
}
