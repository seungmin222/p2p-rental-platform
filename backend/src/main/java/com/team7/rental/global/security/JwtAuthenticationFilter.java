package com.team7.rental.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization: Bearer 헤더의 Access Token을 검증해 인증 정보(principal = memberId)를 세운다.
 * <ul>
 *     <li>헤더 없음: 비회원으로 진행. 보호 경로는 이후 EntryPoint가 401로 응답한다.</li>
 *     <li>헤더가 있는데 무효·만료: 경로와 관계없이 바로 401. 프론트가 재발급할 수 있게 비회원으로 넘기지 않는다.</li>
 *     <li>/api/auth/**: 헤더를 보지 않는다. 만료된 AT가 붙어 와도 재발급·로그아웃이 되어야 하기 때문이다.</li>
 * </ul>
 * SecurityConfig에서만 등록한다(@Component로 만들면 서블릿 필터로 한 번 더 등록된다).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String AUTH_PATH_PREFIX = "/api/auth/";

	private final JwtProvider jwtProvider;
	private final AuthenticationEntryPoint authenticationEntryPoint;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.startsWith(AUTH_PATH_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null) {
			filterChain.doFilter(request, response);
			return;
		}
		try {
			authenticate(header);
		} catch (BusinessException e) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(request, response,
				new InsufficientAuthenticationException("유효하지 않은 Access Token"));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String header) {
		if (!header.startsWith(BEARER_PREFIX)) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		AccessTokenClaims claims = jwtProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
		var authentication = UsernamePasswordAuthenticationToken.authenticated(claims.memberId(), null,
			List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
		authentication.setDetails(claims);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
