package com.team7.rental.payment.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidDepositAction} — grade·amount·reason 필수, 사유 deduction.reason.max */
@RequiredArgsConstructor
public class AdminDepositDecisionRequestValidator
	implements ConstraintValidator<ValidDepositAction, AdminDepositDecisionRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(AdminDepositDecisionRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		return DeductionTexts.validateDecision(request.grade(), request.amount(), request.normalizedReason(),
			policyProperties.deduction().reasonMax(), context);
	}
}
