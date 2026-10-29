package com.team7.rental.member.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidMemberText} 검증기 공용 */
final class MemberTexts {

	/** 비밀번호 구성: 영문·숫자·특수문자(영문·숫자·공백이 아닌 문자)를 모두 포함 (policies.md member.password) */
	static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).*$";
	static final String PASSWORD_PATTERN_MESSAGE = "영문, 숫자, 특수문자를 모두 포함해 주세요.";

	private MemberTexts() {}

	static int length(String text) {
		return text.codePointCount(0, text.length());
	}

	/** null이면 통과(@NotBlank가 따로 검사). 범위를 벗어나면 위반을 추가하고 false */
	static boolean checkLength(String text, PolicyProperties.Length range, String field,
		ConstraintValidatorContext context) {
		if (text == null) {
			return true;
		}
		int length = length(text);
		if (length < range.min() || length > range.max()) {
			return reject(context, field, range.min() + "자 이상 " + range.max() + "자 이하로 입력해 주세요.");
		}
		return true;
	}

	static boolean reject(ConstraintValidatorContext context, String field, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
		return false;
	}
}
