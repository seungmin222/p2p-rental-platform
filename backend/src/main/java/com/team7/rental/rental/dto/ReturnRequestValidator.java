package com.team7.rental.rental.dto;

import java.util.Arrays;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.ReturnChecklistItem;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/**
 * {@link ValidInspectionText} — 반납 체크리스트는 모든 항목이 true/false, false 항목이 있으면 메모 필수, 메모 길이 return.note-max.
 * 모르는 키는 여기까지 오지 않는다(JSON 변환에서 400).
 */
@RequiredArgsConstructor
public class ReturnRequestValidator implements ConstraintValidator<ValidInspectionText, RentalReturnRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(RentalReturnRequest request, ConstraintValidatorContext context) {
		if (request == null || request.checklist() == null) {
			return true;
		}
		boolean complete = Arrays.stream(ReturnChecklistItem.values())
			.allMatch(item -> request.checklist().get(item) != null);
		if (!complete) {
			return InspectionTexts.reject(context, "checklist", "모든 항목을 true 또는 false로 보내 주세요.");
		}
		String note = request.normalizedNote();
		if (request.hasUncheckedItem() && note == null) {
			return InspectionTexts.reject(context, "note", "확인하지 못한 항목이 있으면 필수입니다.");
		}
		int max = policyProperties.returnPolicy().noteMax();
		if (InspectionTexts.exceeds(note, max)) {
			return InspectionTexts.reject(context, "note", max + "자 이하로 입력해 주세요.");
		}
		return true;
	}
}
