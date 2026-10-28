package com.team7.rental.rental.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 취소 사유 텍스트 검증 (REQ-015): reason=OTHER면 필수, 길이는 cancel.reason-text-max 이하. 오류 필드는 reasonText.
 * 길이 상한이 설정값이라 @Size 대신 PolicyProperties를 주입받는 클래스 수준 제약으로 둔다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CancelReasonValidator.class)
public @interface ValidCancelReason{

	String message() default "취소 사유가 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
