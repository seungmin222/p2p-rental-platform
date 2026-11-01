package com.team7.rental.member.service;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.member.dto.AuthLoginRequest;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.support.IntegrationTestSupport;

class AuthFlowIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private AuthService authService;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtProvider jwtProvider;

	private final AuthLoginRequest request = new AuthLoginRequest("auth@example.com", "Rental123!");

	@BeforeEach
	void createMember() {
		memberRepository.saveAndFlush(Member.createLocal(request.email(),
			passwordEncoder.encode(request.password()), "인증테스트", new BigDecimal("50")));
	}

	@Test
	void loginIssuesMatchingFamilyAndSecureCookie() {
		var response = new MockHttpServletResponse();
		var result = authService.login(request, response);
		var access = jwtProvider.parseAccessToken(result.accessToken());
		var refresh = jwtProvider.parseRefreshToken(token(response));
		assertThat(access.memberId()).isEqualTo(refresh.memberId());
		assertThat(access.familyId()).isEqualTo(refresh.familyId()).isNotBlank();
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
			.contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth");
	}

	@Test
	void invalidEmailAndPasswordHaveSameError() {
		for (var invalid : List.of(new AuthLoginRequest("missing@example.com", request.password()),
			new AuthLoginRequest(request.email(), "wrong"))) {
			assertThatThrownBy(() -> authService.login(invalid, new MockHttpServletResponse()))
				.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
		}
	}

	@Test
	void reuseRevokesOnlyItsFamily() {
		String firstDevice = login();
		String secondDevice = login();
		var response = new MockHttpServletResponse();
		authService.reissue(firstDevice, response);
		String rotated = token(response);
		assertThat(rotated).isNotEqualTo(firstDevice);
		assertInvalid(firstDevice);
		assertInvalid(rotated);
		assertThatCode(() -> authService.reissue(secondDevice, new MockHttpServletResponse()))
			.doesNotThrowAnyException();
	}

	@Test
	void logoutRevokesOnlyCurrentDeviceAndClearsCookie() {
		String first = login();
		String second = login();
		var response = new MockHttpServletResponse();
		authService.logout(first, response);
		assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/api/auth");
		assertInvalid(first);
		assertThatCode(() -> authService.reissue(second, new MockHttpServletResponse()))
			.doesNotThrowAnyException();
	}

	@Test
	void absentInvalidAndExpiredTokensAreRejectedButLogoutIsIdempotent() {
		assertInvalid(null);
		assertInvalid("not-a-token");
		String refresh = login();
		testClock.advance(Duration.ofDays(365));
		assertInvalid(refresh);
		for (String value : new String[] {null, "not-a-token", refresh}) {
			var response = new MockHttpServletResponse();
			assertThatCode(() -> authService.logout(value, response)).doesNotThrowAnyException();
			assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
		}
	}

	@Test
	void concurrentRotationHasOneWinnerThenRevokesReusedFamily() throws Exception {
		String original = login();
		var gate = new CountDownLatch(1);
		Callable<String> rotate = () -> {
			gate.await();
			var response = new MockHttpServletResponse();
			try {
				authService.reissue(original, response);
				return token(response);
			} catch (BusinessException e) {
				assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
				return null;
			}
		};
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(rotate);
			var second = executor.submit(rotate);
			gate.countDown();
			String a = first.get();
			String b = second.get();
			assertThat((a == null) != (b == null)).isTrue();
			assertInvalid(a != null ? a : b);
		}
	}

	private String login() {
		var response = new MockHttpServletResponse();
		authService.login(request, response);
		return token(response);
	}

	private String token(MockHttpServletResponse response) {
		String header = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(header).startsWith("refreshToken=");
		return header.substring("refreshToken=".length(), header.indexOf(';'));
	}

	private void assertInvalid(String refresh) {
		assertThatThrownBy(() -> authService.reissue(refresh, new MockHttpServletResponse()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
	}
}
