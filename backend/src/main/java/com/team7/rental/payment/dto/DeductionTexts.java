package com.team7.rental.payment.dto;

import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidDepositAction} 검증기 공용 */
final class DeductionTexts {

	private DeductionTexts() {}

	static boolean exceeds(String text, int max) {
		return text != null && text.codePointCount(0, text.length()) > max;
	}

	static boolean reject(ConstraintValidatorContext context, String field, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
		return false;
	}

	/** grade·amount·reason 필수, reason 길이. 통과하면 true */
	static boolean validateDecision(Object grade, Integer amount, String normalizedReason, int reasonMax,
		ConstraintValidatorContext context) {
		if (grade == null) {
			return reject(context, "grade", "필수입니다.");
		}
		if (amount == null) {
			return reject(context, "amount", "필수입니다.");
		}
		if (normalizedReason == null) {
			return reject(context, "reason", "필수입니다.");
		}
		if (exceeds(normalizedReason, reasonMax)) {
			return reject(context, "reason", reasonMax + "자 이하로 입력해 주세요.");
		}
		return true;
	}
}
