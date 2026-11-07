package com.team7.rental.payment.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 보증금 차감 요청(REQ-049)의 동작별 필수 항목·사유 길이 검증. 상한이 설정값(deduction.reason.max)이라 @Size 대신 PolicyProperties를 주입받는
 * 클래스 수준 제약으로 둔다. 글자 수는 코드 포인트로 센다(이모지 1자).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {DepositActionRequestValidator.class, AdminDepositDecisionRequestValidator.class})
public @interface ValidDepositAction{

	String message() default "입력값이 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
