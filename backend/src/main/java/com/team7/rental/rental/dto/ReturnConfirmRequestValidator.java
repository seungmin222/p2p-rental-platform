package com.team7.rental.rental.dto;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.InspectionResult;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidInspectionText} — ABNORMAL이면 resultNote 필수, 길이 inspection.abnormal-note-max. 사진 장수는 서비스가 검사한다 */
@RequiredArgsConstructor
public class ReturnConfirmRequestValidator
	implements ConstraintValidator<ValidInspectionText, RentalReturnConfirmRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(RentalReturnConfirmRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		String resultNote = request.normalizedResultNote();
		if (request.result() == InspectionResult.ABNORMAL && resultNote == null) {
			return InspectionTexts.reject(context, "resultNote", "이상(ABNORMAL)이면 필수입니다.");
		}
		int max = policyProperties.inspection().abnormalNoteMax();
		if (InspectionTexts.exceeds(resultNote, max)) {
			return InspectionTexts.reject(context, "resultNote", max + "자 이하로 입력해 주세요.");
		}
		return true;
	}
}
