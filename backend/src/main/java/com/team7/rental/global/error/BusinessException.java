package com.team7.rental.global.error;

import java.util.List;

import com.team7.rental.global.response.FieldErrorDetail;

import lombok.Getter;

/**
 * 모든 비즈니스 예외. 도메인별·상황별 예외 클래스를 만들지 않고 ErrorCode로 구분한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<FieldErrorDetail> details;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, List.of());
	}

	public BusinessException(ErrorCode errorCode, List<FieldErrorDetail> details) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.details = List.copyOf(details);
	}
}
