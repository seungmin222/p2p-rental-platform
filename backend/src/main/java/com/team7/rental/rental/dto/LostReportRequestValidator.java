package com.team7.rental.rental.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidInspectionText} — 미반납 신고 메모 길이 return.note-max (item_inspections.note와 같은 칸) */
@RequiredArgsConstructor
public class LostReportRequestValidator implements ConstraintValidator<ValidInspectionText, RentalLostReportRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(RentalLostReportRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		int max = policyProperties.returnPolicy().noteMax();
		if (InspectionTexts.exceeds(request.normalizedNote(), max)) {
			return InspectionTexts.reject(context, "note", max + "자 이하로 입력해 주세요.");
		}
		return true;
	}
}
