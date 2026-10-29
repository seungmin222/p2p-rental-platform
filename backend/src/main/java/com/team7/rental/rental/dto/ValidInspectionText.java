package com.team7.rental.rental.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 인계·반납·상태 확인 요청(REQ-017·018)의 글자 수·조건부 필수 검증. 상한이 설정값(inspection.*, return.*)이라 @Size 대신
 * PolicyProperties를 주입받는 클래스 수준 제약으로 두고, 요청 타입마다 검증기를 둔다. 글자 수는 코드 포인트로 센다(이모지 1자).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {HandoverRequestValidator.class, ReturnRequestValidator.class,
	ReturnConfirmRequestValidator.class})
public @interface ValidInspectionText{

	String message() default "입력값이 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
