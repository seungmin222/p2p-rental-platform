package com.team7.rental.global.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

class JwtProviderTest {

	private static final String SECRET = "test-jwt-secret-must-be-at-least-32-bytes-long";
	private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
	private static final Duration REFRESH_TTL = Duration.ofDays(14);
	private static final Instant NOW = Instant.parse("2026-10-10T03:00:00Z");

	private final JwtProvider jwtProvider = providerAt(NOW);

	@Test
	@DisplayName("Access Token에서 memberId와 role을 꺼낸다")
	void parseValidAccessToken() {
		// given
		String token = jwtProvider.createAccessToken(1L, "USER");

		// when
		AccessTokenClaims claims = jwtProvider.parseAccessToken(token);

		// then
		assertThat(claims).isEqualTo(new AccessTokenClaims(1L, "USER"));
	}

	@Test
	@DisplayName("Access Token은 만료 직전까지 유효하고, 만료 시각이 지나면 거부된다")
	void accessTokenExpiresByClock() {
		// given
		String token = jwtProvider.createAccessToken(1L, "USER");

		// when, then
		assertThat(providerAt(NOW.plus(ACCESS_TTL).minusSeconds(1)).parseAccessToken(token).memberId()).isEqualTo(1L);
		assertUnauthorized(() -> providerAt(NOW.plus(ACCESS_TTL).plusSeconds(1)).parseAccessToken(token));
	}

	@Test
	@DisplayName("다른 키로 서명된 토큰은 거부된다")
	void rejectsInvalidSignature() {
		// given
		JwtProvider other = new JwtProvider(
			new JwtProperties("another-secret-that-is-also-32-bytes-long!!", ACCESS_TTL, REFRESH_TTL), clockAt(NOW));
		String token = other.createAccessToken(1L, "USER");

		// when, then
		assertUnauthorized(() -> jwtProvider.parseAccessToken(token));
	}

	@Test
	@DisplayName("형식이 잘못된 토큰은 거부된다")
	void rejectsMalformedToken() {
		assertUnauthorized(() -> jwtProvider.parseAccessToken("not-a-jwt"));
		assertUnauthorized(() -> jwtProvider.parseAccessToken(""));
		assertUnauthorized(() -> jwtProvider.parseAccessToken(null));
	}

	@Test
	@DisplayName("Refresh Token은 familyId와 jti를 담고, 만료 시각은 Clock 기준 14일 뒤다")
	void refreshTokenCarriesFamilyAndJti() {
		// when
		IssuedRefreshToken issued = jwtProvider.createRefreshToken(1L, "family-1");
		RefreshTokenClaims claims = jwtProvider.parseRefreshToken(issued.token());

		// then
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
		assertThat(claims.memberId()).isEqualTo(1L);
		assertThat(claims.familyId()).isEqualTo("family-1");
		assertThat(claims.jti()).isEqualTo(issued.jti()).isNotBlank();
	}

	@Test
	@DisplayName("재발급할 때마다 jti가 달라진다")
	void refreshTokenJtiIsUnique() {
		// when
		IssuedRefreshToken first = jwtProvider.createRefreshToken(1L, "family-1");
		IssuedRefreshToken second = jwtProvider.createRefreshToken(1L, "family-1");

		// then
		assertThat(first.jti()).isNotEqualTo(second.jti());
	}

	@Test
	@DisplayName("만료된 Refresh Token은 거부된다")
	void refreshTokenExpires() {
		// given
		String token = jwtProvider.createRefreshToken(1L, "family-1").token();

		// when, then
		assertUnauthorized(() -> providerAt(NOW.plus(REFRESH_TTL).plusSeconds(1)).parseRefreshToken(token));
	}

	@Test
	@DisplayName("AT에는 typ=ACCESS, RT에는 typ=REFRESH 클레임이 들어간다")
	void tokensCarryTypeClaim() {
		// when
		String accessPayload = payloadOf(jwtProvider.createAccessToken(1L, "USER"));
		String refreshPayload = payloadOf(jwtProvider.createRefreshToken(1L, "family-1").token());

		// then
		assertThat(accessPayload).contains("\"typ\":\"ACCESS\"").doesNotContain("familyId");
		assertThat(refreshPayload).contains("\"typ\":\"REFRESH\"").doesNotContain("role");
	}

	@Test
	@DisplayName("Refresh Token을 Access Token으로, Access Token을 Refresh Token으로 쓸 수 없다")
	void rejectsWrongTokenType() {
		// given
		String accessToken = jwtProvider.createAccessToken(1L, "USER");
		String refreshToken = jwtProvider.createRefreshToken(1L, "family-1").token();

		// when, then
		assertUnauthorized(() -> jwtProvider.parseAccessToken(refreshToken));
		assertUnauthorized(() -> jwtProvider.parseRefreshToken(accessToken));
	}

	@Test
	@DisplayName("JWT 비밀키가 32바이트 미만이면 생성(기동)에 실패한다")
	void rejectsShortSecret() {
		// given
		JwtProperties shortSecret = new JwtProperties("a".repeat(31), ACCESS_TTL, REFRESH_TTL);
		JwtProperties missingSecret = new JwtProperties(null, ACCESS_TTL, REFRESH_TTL);

		// when, then
		assertThatThrownBy(() -> new JwtProvider(shortSecret, clockAt(NOW)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("32바이트");
		assertThatThrownBy(() -> new JwtProvider(missingSecret, clockAt(NOW)))
			.isInstanceOf(IllegalStateException.class);
	}

	private static String payloadOf(String token) {
		return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
	}

	private static JwtProvider providerAt(Instant instant) {
		return new JwtProvider(new JwtProperties(SECRET, ACCESS_TTL, REFRESH_TTL), clockAt(instant));
	}

	private static Clock clockAt(Instant instant) {
		return Clock.fixed(instant, ZoneId.of("Asia/Seoul"));
	}

	private static void assertUnauthorized(ThrowingCallable callable) {
		assertThatThrownBy(callable)
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.AUTH_UNAUTHORIZED);
	}
}
