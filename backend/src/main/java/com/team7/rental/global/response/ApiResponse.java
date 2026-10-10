package com.team7.rental.global.response;

import java.util.List;

import com.team7.rental.global.error.ErrorCode;

/**
 * 팀 공통 응답 형식 (docs/architecture.md §4). 성공이면 error가 null, 실패면 data가 null이다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	public static ApiResponse<Void> fail(ErrorCode errorCode) {
		return fail(errorCode, List.of());
	}

	public static ApiResponse<Void> fail(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, details));
	}
}
