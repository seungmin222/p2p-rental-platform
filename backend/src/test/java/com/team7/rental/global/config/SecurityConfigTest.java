package com.team7.rental.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.JwtProperties;
import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.global.security.SecurityErrorResponder;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class, properties = "app.jwt.secret=test-jwt-secret-must-be-at-least-32-bytes-long")
@Import({SecurityConfig.class, JwtProvider.class, SecurityErrorResponder.class, TimeConfig.class,
	SecurityConfigTest.TestController.class})
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	@DisplayName("공개 경로는 토큰 없이 접근할 수 있다")
	void publicPaths() throws Exception {
		mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
		mockMvc.perform(post("/api/members")).andExpect(status().isOk());
		mockMvc.perform(get("/api/items/1")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("보호 경로에 토큰 없이 접근하면 401 AUTH_UNAUTHORIZED 공통 응답이다")
	void protectedPathWithoutToken() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/members/me")));
	}

	@Test
	@DisplayName("공개 패턴과 겹치는 회원 전용 경로는 토큰이 필요하다")
	void memberOnlyPathsOverlappingPublicPatterns() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/api/items/deposit-guide")));
		expectUnauthorized(mockMvc.perform(post("/api/auth/logout")));
	}

	@Test
	@DisplayName("유효한 Access Token이면 @LoginMember로 memberId가 주입된다")
	void validToken() throws Exception {
		mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").value(7));
	}

	@Test
	@DisplayName("만료·서명 불일치·형식 오류 토큰으로 보호 경로에 접근하면 401이다")
	void invalidTokens() throws Exception {
		JwtProvider past = new JwtProvider(jwtProperties,
			Clock.fixed(Instant.now().minus(jwtProperties.accessTokenTtl()).minusSeconds(1), ZoneId.of("Asia/Seoul")));
		JwtProvider otherKey = new JwtProvider(
			new JwtProperties("another-secret-that-is-also-32-bytes-long!!", jwtProperties.accessTokenTtl(),
				jwtProperties.refreshTokenTtl()),
			Clock.systemUTC());

		expectUnauthorized(mockMvc.perform(get("/api/members/me")
			.header(HttpHeaders.AUTHORIZATION, bearer(past.createAccessToken(7L, "USER")))));
		expectUnauthorized(mockMvc.perform(get("/api/members/me")
			.header(HttpHeaders.AUTHORIZATION, bearer(otherKey.createAccessToken(7L, "USER")))));
		expectUnauthorized(mockMvc.perform(get("/api/members/me")
			.header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt"))));
		expectUnauthorized(mockMvc.perform(get("/api/members/me")
			.header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createRefreshToken(7L, "family-1").token()))));
	}

	@Test
	@DisplayName("비회원 허용 경로는 토큰이 있으면 memberId, 없거나 무효면 비회원(null)으로 처리한다")
	void optionalLogin() throws Exception {
		mockMvc.perform(get("/api/items/1").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
			.andExpect(jsonPath("$.data").value(7));
		mockMvc.perform(get("/api/items/1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());
		mockMvc.perform(get("/api/items/1").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("관리자 경로에 일반 회원이 접근하면 403 AUTH_FORBIDDEN 공통 응답이다")
	void adminPathWithUserToken() throws Exception {
		expectForbidden(mockMvc.perform(get("/api/admin/ping").header(HttpHeaders.AUTHORIZATION, bearer(userToken()))));
		mockMvc.perform(get("/api/admin/ping")
			.header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(1L, "ADMIN"))))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("메서드 보안 거부는 500이 아니라 회원 403, 비회원 401이다")
	void methodSecurity() throws Exception {
		expectForbidden(mockMvc.perform(get("/api/rentals/admin-only")
			.header(HttpHeaders.AUTHORIZATION, bearer(userToken()))));
		expectUnauthorized(mockMvc.perform(get("/api/categories")));
	}

	@Test
	@DisplayName("Swagger 경로는 설정이 꺼져 있으면(local 외) 공개되지 않는다")
	void swaggerNotPublicByDefault() throws Exception {
		expectUnauthorized(mockMvc.perform(get("/v3/api-docs")));
		expectUnauthorized(mockMvc.perform(get("/swagger-ui/index.html")));
	}

	@Test
	@DisplayName("다른 출처의 CORS 요청에 허용 헤더를 주지 않는다")
	void corsBlocked() throws Exception {
		mockMvc.perform(options("/api/items/1")
			.header(HttpHeaders.ORIGIN, "http://evil.example")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
		mockMvc.perform(get("/api/items/1").header(HttpHeaders.ORIGIN, "http://evil.example"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	@DisplayName("세션을 만들지 않는다")
	void stateless() throws Exception {
		mockMvc.perform(get("/api/members/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
			.andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	private String userToken() {
		return jwtProvider.createAccessToken(7L, "USER");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private static void expectUnauthorized(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	private static void expectForbidden(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
	}

	@RestController
	static class TestController {

		@PostMapping({"/api/auth/login", "/api/auth/logout", "/api/members"})
		ApiResponse<Void> ok() {
			return ApiResponse.ok();
		}

		@GetMapping("/api/members/me")
		ApiResponse<Long> me(@LoginMember
		Long memberId) {
			return ApiResponse.ok(memberId);
		}

		@GetMapping("/api/items/{itemId}")
		ApiResponse<Long> item(@PathVariable
		String itemId, @LoginMember(required = false)
		Long memberId) {
			return ApiResponse.ok(memberId);
		}

		@GetMapping("/api/admin/ping")
		ApiResponse<Void> admin() {
			return ApiResponse.ok();
		}

		@PreAuthorize("hasRole('ADMIN')")
		@GetMapping("/api/rentals/admin-only")
		ApiResponse<Void> adminOnly() {
			return ApiResponse.ok();
		}

		/** 경로는 공개(GET /api/categories)지만 메서드 보안으로 로그인 요구 */
		@PreAuthorize("isAuthenticated()")
		@GetMapping("/api/categories")
		ApiResponse<Void> membersOnly() {
			return ApiResponse.ok();
		}
	}
}
