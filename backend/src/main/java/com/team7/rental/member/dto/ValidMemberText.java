package com.team7.rental.member.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 회원 입력(REQ-001·005)의 글자 수 검증. 범위가 설정값(member.*)이라 @Size 대신 PolicyProperties를 주입받는 클래스 수준 제약으로 두고,
 * 요청 타입마다 검증기를 둔다(대여 {@code ValidInspectionText}와 같은 방식). 글자 수는 코드 포인트로 센다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {MemberSignupRequestValidator.class, MemberProfileUpdateRequestValidator.class,
	MemberPasswordChangeRequestValidator.class})
public @interface ValidMemberText{

	String message() default "입력값이 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
