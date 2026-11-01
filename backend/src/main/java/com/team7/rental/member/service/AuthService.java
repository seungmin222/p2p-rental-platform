package com.team7.rental.member.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.security.IssuedRefreshToken;
import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.global.security.RefreshTokenClaims;
import com.team7.rental.member.dto.AuthLoginRequest;
import com.team7.rental.member.dto.AuthTokenResponse;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RefreshTokenRepository;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** 로그인·로그아웃·재발급 (REQ-002·003). RT는 HttpOnly 쿠키로만 전달한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {
	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final Clock clock;
	private final Environment environment;

	@Transactional
	public AuthTokenResponse login(AuthLoginRequest request, HttpServletResponse response) {
		Member member = memberRepository.findByEmailForUpdate(request.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
		if (member.getStatus() == MemberStatus.WITHDRAWN || member.getPassword() == null
			|| !passwordEncoder.matches(request.password(), member.getPassword())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		String familyId = UUID.randomUUID().toString();
		IssuedRefreshToken refresh = jwtProvider.createRefreshToken(member.getId(), familyId);
		String access = jwtProvider.createAccessToken(member.getId(), member.getRole().name(), familyId);
		Duration ttl = remaining(refresh);
		refreshTokenRepository.save(member.getId(), familyId, refresh.jti(), ttl);
		writeCookie(response, refresh.token(), ttl);
		return new AuthTokenResponse(access);
	}

	public void logout(String refreshToken, HttpServletResponse response) {
		RefreshTokenClaims claims = null;
		if (refreshToken != null && !refreshToken.isBlank()) {
			try {
				claims = jwtProvider.parseRefreshToken(refreshToken);
			} catch (BusinessException ignored) {
				// 무효·만료된 토큰이어도 로그아웃은 성공한다.
			}
		}
		if (claims != null) {
			refreshTokenRepository.delete(claims.memberId(), claims.familyId());
		}
		writeCookie(response, "", Duration.ZERO);
	}

	@Transactional
	public AuthTokenResponse reissue(String refreshToken, HttpServletResponse response) {
		try {
			RefreshTokenClaims claims = parseRefresh(refreshToken);
			Member member = memberRepository.findForUpdate(claims.memberId())
				.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
			if (member.getStatus() == MemberStatus.WITHDRAWN) {
				refreshTokenRepository.delete(claims.memberId(), claims.familyId());
				throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
			}
			IssuedRefreshToken next = jwtProvider.createRefreshToken(member.getId(), claims.familyId());
			String access = jwtProvider.createAccessToken(member.getId(), member.getRole().name(), claims.familyId());
			Duration ttl = remaining(next);
			if (!refreshTokenRepository.rotate(member.getId(), claims.familyId(), claims.jti(), next.jti(), ttl)) {
				throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
			}
			writeCookie(response, next.token(), ttl);
			return new AuthTokenResponse(access);
		} catch (BusinessException e) {
			writeCookie(response, "", Duration.ZERO);
			throw e;
		}
	}

	private RefreshTokenClaims parseRefresh(String token) {
		if (token == null || token.isBlank()) {
			throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
		}
		try {
			return jwtProvider.parseRefreshToken(token);
		} catch (BusinessException e) {
			throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
		}
	}

	private Duration remaining(IssuedRefreshToken token) {
		return Duration.between(clock.instant(), token.expiresAt());
	}

	private void writeCookie(HttpServletResponse response, String token, Duration ttl) {
		ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
			.httpOnly(true)
			.secure(!environment.acceptsProfiles(Profiles.of("local")))
			.sameSite("Lax")
			.path("/api/auth")
			.maxAge(ttl)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
