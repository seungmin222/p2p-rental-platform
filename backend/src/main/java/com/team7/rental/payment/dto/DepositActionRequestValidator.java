package com.team7.rental.payment.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidDepositAction} — PROPOSE면 grade·amount·reason 필수(사유 deduction.reason.max), ACCEPT·REJECT면 proposalId 필수 */
@RequiredArgsConstructor
public class DepositActionRequestValidator implements ConstraintValidator<ValidDepositAction, DepositActionRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(DepositActionRequest request, ConstraintValidatorContext context) {
		if (request == null || request.action() == null) {
			return true;
		}
		if (request.action() == DepositAction.PROPOSE) {
			return DeductionTexts.validateDecision(request.grade(), request.amount(), request.normalizedReason(),
				policyProperties.deduction().reasonMax(), context);
		}
		if (request.proposalId() == null) {
			return DeductionTexts.reject(context, "proposalId", "필수입니다.");
		}
		return true;
	}
}
