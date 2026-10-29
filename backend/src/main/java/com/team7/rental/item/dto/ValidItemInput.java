package com.team7.rental.item.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 물품 입력(REQ-006·007·010)의 글자 수·금액 범위·기간 검증. 범위가 설정값(item.*)이거나 필드 사이 조건이라 클래스 수준 제약으로 두고,
 * 요청 타입마다 검증기를 둔다(대여 {@code ValidInspectionText}와 같은 방식). 글자 수는 코드 포인트로 센다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ItemCreateRequestValidator.class, ItemUpdateRequestValidator.class,
	ItemDepositGuideRequestValidator.class, ItemSearchRequestValidator.class})
public @interface ValidItemInput{

	String message() default "입력값이 올바르지 않습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
