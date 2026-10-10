package com.team7.rental.global.error;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 낼 수 있는 에러 코드를 선언한다. Swagger 응답에 상태별로 code·message 예시가 표시된다.
 * 도메인 문서의 "예외" 항목과 일치시킨다. 예: {@code @ApiErrorCodes({RENTAL_PERIOD_CONFLICT, RENTAL_OWN_ITEM})}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes{

	ErrorCode[] value();
}
