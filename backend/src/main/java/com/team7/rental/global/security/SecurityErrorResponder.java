package com.team7.rental.global.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * 필터 단계(컨트롤러 도달 전)의 인증 실패·권한 없음을 팀 공통 응답 형식으로 쓴다.
 * 컨트롤러 안에서 난 보안 예외(메서드 보안)는 GlobalExceptionHandler가 같은 코드로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	/** 인증 정보 없음·토큰 무효 → 401 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		write(response, ErrorCode.AUTH_UNAUTHORIZED);
	}

	/** 인증은 됐지만 권한 없음 → 403 */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException {
		write(response, ErrorCode.AUTH_FORBIDDEN);
	}

	private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), ApiResponse.fail(errorCode));
	}
}
