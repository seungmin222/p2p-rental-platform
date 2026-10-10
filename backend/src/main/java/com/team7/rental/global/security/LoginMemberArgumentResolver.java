package com.team7.rental.global.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class) && Long.class.equals(parameter.getParameterType());
	}

	@Override
	public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		// 비회원은 AnonymousAuthenticationToken(principal이 문자열)이라 Long이 아니다
		if (authentication != null && authentication.getPrincipal() instanceof Long memberId) {
			return memberId;
		}
		if (parameter.getParameterAnnotation(LoginMember.class).required()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return null;
	}
}
