package com.team7.rental.rental.dto;

import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidInspectionText} 검증기 공용 */
final class InspectionTexts {

	private InspectionTexts() {}

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	static String normalize(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	static boolean exceeds(String text, int max) {
		return text != null && text.codePointCount(0, text.length()) > max;
	}

	static boolean reject(ConstraintValidatorContext context, String field, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
		return false;
	}
}
