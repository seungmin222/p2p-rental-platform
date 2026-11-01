package com.team7.rental.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT 발급·검증. 만료 시각은 Clock 빈 기준으로 계산한다.
 * Refresh Token의 Redis 저장·Rotation·재사용 감지는 이 클래스의 책임이 아니다(member 도메인, REQ-002·003).
 */
@Slf4j
@Component
public class JwtProvider {

	static final int MIN_SECRET_BYTES = 32;
	private static final String ROLE_CLAIM = "role";
	private static final String FAMILY_ID_CLAIM = "familyId";
	// 토큰 종류. AT·RT를 서로 바꿔 쓰지 못하게 검증한다
	private static final String TYPE_CLAIM = "typ";
	private static final String ACCESS_TYPE = "ACCESS";
	private static final String REFRESH_TYPE = "REFRESH";

	private final JwtProperties properties;
	private final Clock clock;
	private final SecretKey key;
	private final JwtParser parser;

	public JwtProvider(JwtProperties properties, Clock clock) {
		byte[] secret = properties.secret() == null ? new byte[0]
			: properties.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
				"app.jwt.secret(JWT_SECRET)은 " + MIN_SECRET_BYTES + "바이트(256bit) 이상이어야 합니다.");
		}
		this.properties = properties;
		this.clock = clock;
		this.key = Keys.hmacShaKeyFor(secret);
		this.parser = Jwts.parser()
			.verifyWith(key)
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	public String createAccessToken(Long memberId, String role) {
		return createAccessToken(memberId, role, null);
	}

	public String createAccessToken(Long memberId, String role, String familyId) {
		Instant now = clock.instant();
		return Jwts.builder()
			.subject(memberId.toString())
			.claim(TYPE_CLAIM, ACCESS_TYPE)
			.claim(ROLE_CLAIM, role)
			.claim(FAMILY_ID_CLAIM, familyId)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.accessTokenTtl())))
			.signWith(key)
			.compact();
	}

	/**
	 * @param familyId 로그인마다 새로 만든 값, 재발급 시에는 기존 값을 그대로 넘긴다
	 */
	public IssuedRefreshToken createRefreshToken(Long memberId, String familyId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(properties.refreshTokenTtl());
		String jti = UUID.randomUUID().toString();
		String token = Jwts.builder()
			.subject(memberId.toString())
			.claim(TYPE_CLAIM, REFRESH_TYPE)
			.claim(FAMILY_ID_CLAIM, familyId)
			.id(jti)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(key)
			.compact();
		return new IssuedRefreshToken(token, familyId, jti, expiresAt);
	}

	/**
	 * @throws BusinessException AUTH_UNAUTHORIZED: 만료, 서명 불일치, 형식 오류, Refresh Token을 넣은 경우
	 */
	public AccessTokenClaims parseAccessToken(String token) {
		Claims claims = parse(token);
		String role = claims.get(ROLE_CLAIM, String.class);
		if (!ACCESS_TYPE.equals(claims.get(TYPE_CLAIM, String.class)) || role == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return new AccessTokenClaims(toMemberId(claims), role, claims.get(FAMILY_ID_CLAIM, String.class));
	}

	/**
	 * @throws BusinessException AUTH_UNAUTHORIZED: 만료, 서명 불일치, 형식 오류, Access Token을 넣은 경우
	 */
	public RefreshTokenClaims parseRefreshToken(String token) {
		Claims claims = parse(token);
		String familyId = claims.get(FAMILY_ID_CLAIM, String.class);
		if (!REFRESH_TYPE.equals(claims.get(TYPE_CLAIM, String.class)) || familyId == null
			|| claims.getId() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return new RefreshTokenClaims(toMemberId(claims), familyId, claims.getId());
	}

	private Claims parse(String token) {
		try {
			return parser.parseSignedClaims(token).getPayload();
		} catch (JwtException | IllegalArgumentException e) {
			// 토큰 값은 로그에 남기지 않는다
			log.debug("JWT 검증 실패: {}", e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}

	private Long toMemberId(Claims claims) {
		try {
			return Long.valueOf(claims.getSubject());
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
	}
}
