package com.team7.rental.global.error;

import lombok.Getter;

/**
 * 모든 비즈니스 예외. 도메인별·상황별 예외 클래스를 만들지 않고 ErrorCode로 구분한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
