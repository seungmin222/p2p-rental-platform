package com.team7.rental.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.team7.rental.global.error.BusinessException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer 헤더의 Access Token을 검증해 인증 정보(principal = memberId)를 세운다.
 * 토큰이 없거나 유효하지 않으면 비회원으로 계속 진행한다. 보호 경로는 이후 EntryPoint가 401로 응답하고,
 * 비회원도 허용된 경로는 비회원으로 처리된다.
 * SecurityConfig에서만 등록한다(@Component로 만들면 서블릿 필터로 한 번 더 등록된다).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			authenticate(header.substring(BEARER_PREFIX.length()));
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String token) {
		try {
			AccessTokenClaims claims = jwtProvider.parseAccessToken(token);
			var authentication = UsernamePasswordAuthenticationToken.authenticated(claims.memberId(), null,
				List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (BusinessException e) {
			SecurityContextHolder.clearContext();
		}
	}
}
