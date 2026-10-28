package com.team7.rental.rental.dto;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.CancelReason;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidCancelReason} 구현. 스프링 검증기가 PolicyProperties를 주입한다 */
@RequiredArgsConstructor
public class CancelReasonValidator implements ConstraintValidator<ValidCancelReason, RentalCancelRequest> {

	private static final String FIELD = "reasonText";

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(RentalCancelRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		String reasonText = request.normalizedReasonText();
		if (request.reason() == CancelReason.OTHER && reasonText == null) {
			return reject(context, "사유가 기타(OTHER)이면 필수입니다.");
		}
		int max = policyProperties.cancel().reasonTextMax();
		if (reasonText != null && reasonText.length() > max) {
			return reject(context, max + "자 이하로 입력해 주세요.");
		}
		return true;
	}

	private boolean reject(ConstraintValidatorContext context, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(FIELD).addConstraintViolation();
		return false;
	}
}
