package com.team7.rental.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 현재 로그인 회원의 id를 주입한다: {@code @LoginMember Long memberId}.
 * 비회원도 허용된 API(인증 "옵션")에서는 {@code @LoginMember(required = false)}로 받으면 비회원일 때 null이다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface LoginMember{

	/** true인데 로그인하지 않았으면 401 AUTH_UNAUTHORIZED */
	boolean required() default true;
}
