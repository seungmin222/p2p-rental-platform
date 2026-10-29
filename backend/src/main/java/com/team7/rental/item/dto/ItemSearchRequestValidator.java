package com.team7.rental.item.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidItemInput} — 검색 기간: 시작일·종료일은 둘 다 보내거나 둘 다 생략, 시작일 ≤ 종료일 */
public class ItemSearchRequestValidator implements ConstraintValidator<ValidItemInput, ItemSearchRequest> {

	@Override
	public boolean isValid(ItemSearchRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		if ((request.startDate() == null) != (request.endDate() == null)) {
			return ItemInputs.reject(context, request.startDate() == null ? "startDate" : "endDate",
				"시작일과 종료일을 함께 보내 주세요.");
		}
		if (request.startDate() != null && request.startDate().isAfter(request.endDate())) {
			return ItemInputs.reject(context, "endDate", "종료일은 시작일보다 빠를 수 없습니다.");
		}
		return true;
	}
}
