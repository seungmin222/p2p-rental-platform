package com.team7.rental.global.response;

import java.util.List;

import com.team7.rental.global.error.ErrorCode;

/**
 * 실패 응답의 error 필드. details는 필드 검증 오류일 때만 채워지고, 그 외에는 빈 배열이다.
 */
public record ErrorResponse(String code, String message, List<FieldErrorDetail> details) {

	public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> details) {
		return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.copyOf(details));
	}
}
