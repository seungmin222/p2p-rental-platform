package com.team7.rental.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 에러 코드를 한 곳에서 관리한다. 이름은 {DOMAIN}_{REASON} (docs/architecture.md §4).
 * 도메인 코드는 각 도메인 작업에서 아래에 도메인별로 묶어 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

	// 인증·인가
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

	private final HttpStatus status;
	private final String message;

	public String getCode() {
		return name();
	}
}
