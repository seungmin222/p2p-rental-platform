package com.team7.rental.member.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.support.FakePasswordResetMailSender.Kind;
import com.team7.rental.support.FakePasswordResetMailSender.Mail;
import com.team7.rental.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/** 비밀번호 재설정 (REQ-036) */
class PasswordResetIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "reset@example.com";
	private static final String OLD_PASSWORD = "rental123!";
	private static final String NEW_PASSWORD = "newpass456@";

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PolicyProperties policyProperties;

	private Member member;

	@BeforeEach
	void setUp() {
		member = memberRepository.save(Member.createLocal(EMAIL, passwordEncoder.encode(OLD_PASSWORD), "재설정회원",
			policyProperties.trust().initialScore()));
	}

	@Test
	@DisplayName("요청: 가입한 이메일이면 200(data null)이고 1회용 링크 메일을 보낸다. 토큰은 30분, 요청 제한은 5분 TTL")
	void requestSendsLink() throws Exception {
		request(EMAIL).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

		assertThat(passwordResetMail.sent()).hasSize(1);
		Mail mail = passwordResetMail.sent().getFirst();
		assertThat(mail.kind()).isEqualTo(Kind.RESET_LINK);
		assertThat(mail.to()).isEqualTo(EMAIL);
		assertThat(mail.resetUrl()).startsWith("http://localhost:5173/password-reset?token=");
		assertThat(mail.validMinutes()).isEqualTo(policyProperties.passwordReset().tokenTtl().toMinutes());
		String token = token(mail);
		assertThat(token).hasSize(43);
		assertThat(redis.opsForValue().get("pwreset:" + token)).isEqualTo(member.getId().toString());
		assertTtl("pwreset:" + token, policyProperties.passwordReset().tokenTtl());
		assertTtl("pwreset:rate:" + EMAIL, policyProperties.passwordReset().rateLimit());
	}

	@Test
	@DisplayName("요청: 없는 이메일·탈퇴 회원은 같은 200이고 메일을 보내지 않는다")
	void requestSameResponseWithoutMail() throws Exception {
		request("nobody@example.com").andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
		jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", member.getId());
		request(EMAIL).andExpect(status().isOk());

		assertThat(passwordResetMail.sent()).isEmpty();
		assertThat(redis.keys("pwreset:*")).containsExactlyInAnyOrder("pwreset:rate:nobody@example.com",
			"pwreset:rate:" + EMAIL);
	}

	@Test
	@DisplayName("요청 제한: 5분 안의 재요청은 대소문자가 달라도 같은 200이고 메일을 보내지 않는다. 제한이 풀리면 다시 보낸다")
	void rateLimit() throws Exception {
		request(EMAIL).andExpect(status().isOk());
		request("Reset@Example.com").andExpect(status().isOk());
		assertThat(passwordResetMail.sent()).hasSize(1);

		redis.delete("pwreset:rate:" + EMAIL);
		request("Reset@Example.com").andExpect(status().isOk());
		assertThat(passwordResetMail.sent()).hasSize(2);
	}

	@Test
	@DisplayName("요청: 메일 발송이 실패해도 200이다")
	void mailFailureStillOk() throws Exception {
		passwordResetMail.failNext();

		request(EMAIL).andExpect(status().isOk());
		assertThat(passwordResetMail.sent()).isEmpty();
	}

	@Test
	@DisplayName("소셜 전용 계정(비밀번호 없음)은 링크 대신 카카오 로그인 안내 메일을 보내고 토큰을 만들지 않는다")
	void socialAccountNotice() throws Exception {
		jdbcTemplate.update("UPDATE members SET password = NULL, provider = 'KAKAO' WHERE id = ?", member.getId());

		request(EMAIL).andExpect(status().isOk());

		assertThat(passwordResetMail.sent()).extracting(Mail::kind).containsExactly(Kind.SOCIAL_NOTICE);
		assertThat(redis.keys("pwreset:*")).containsExactly("pwreset:rate:" + EMAIL);
	}

	@Test
	@DisplayName("재설정: 새 비밀번호로 로그인되고 옛 비밀번호는 401, 기존 기기 Refresh Token도 401(전체 로그아웃). 같은 토큰은 다시 쓸 수 없다")
	void confirm() throws Exception {
		Cookie oldDevice = login(OLD_PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
			.getCookie("refreshToken");
		request(EMAIL);
		String token = token(passwordResetMail.sent().getFirst());

		confirm(token, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());

		login(NEW_PASSWORD).andExpect(status().isOk());
		login(OLD_PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
		mockMvc.perform(post("/api/auth/reissue").cookie(oldDevice))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_REFRESH_TOKEN_INVALID"));
		confirm(token, "another789#").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("AUTH_RESET_TOKEN_INVALID"));
		assertThat(redis.hasKey("pwreset:" + token)).isFalse();
	}

	@Test
	@DisplayName("재설정: 없는(만료·위조) 토큰과 토큰 발급 뒤 탈퇴한 회원은 400 AUTH_RESET_TOKEN_INVALID")
	void confirmInvalidToken() throws Exception {
		confirm("no-such-token", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("AUTH_RESET_TOKEN_INVALID"));

		request(EMAIL);
		String token = token(passwordResetMail.sent().getFirst());
		jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", member.getId());
		confirm(token, NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("AUTH_RESET_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("재설정: 새 비밀번호가 규칙(member.password)에 어긋나면 400 details newPassword이고 토큰은 그대로 남는다")
	void confirmValidation() throws Exception {
		request(EMAIL);
		String token = token(passwordResetMail.sent().getFirst());

		for (String invalid : new String[] {"abc123!", "abcdefghij1234567890!", "abcd1234"}) {
			confirm(token, invalid).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
				.andExpect(jsonPath("$.error.details[*].field", everyItem(is("newPassword"))));
		}
		mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"not-an-email\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[*].field", contains("email")));
		confirm(token, NEW_PASSWORD).andExpect(status().isOk());
	}

	private ResultActions request(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"" + email + "\"}"));
	}

	private ResultActions confirm(String token, String newPassword) throws Exception {
		return mockMvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword)));
	}

	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, password)));
	}

	private static String token(Mail mail) {
		return mail.resetUrl().substring(mail.resetUrl().indexOf("token=") + "token=".length());
	}

	/** TTL이 정책 값 이하이고 1분 넘게 줄지 않았는지 */
	private void assertTtl(String key, Duration expected) {
		Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
		assertThat(seconds).isNotNull().isBetween(expected.minusMinutes(1).toSeconds(), expected.toSeconds());
	}
}
