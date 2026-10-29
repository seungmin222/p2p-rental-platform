package com.team7.rental.rental.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidInspectionText} — 인계 메모 길이(inspection.handover-memo-max) */
@RequiredArgsConstructor
public class HandoverRequestValidator implements ConstraintValidator<ValidInspectionText, RentalHandoverRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(RentalHandoverRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		int max = policyProperties.inspection().handoverMemoMax();
		if (InspectionTexts.exceeds(request.normalizedMemo(), max)) {
			return InspectionTexts.reject(context, "memo", max + "자 이하로 입력해 주세요.");
		}
		return true;
	}
}
