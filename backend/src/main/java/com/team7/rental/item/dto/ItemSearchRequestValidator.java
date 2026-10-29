package com.team7.rental.item.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidItemInput} — 검색: 시작일·종료일은 둘 다 보내거나 둘 다 생략, 시작일 ≤ 종료일, minPrice ≤ maxPrice. 위반은 모두 모은다 */
public class ItemSearchRequestValidator implements ConstraintValidator<ValidItemInput, ItemSearchRequest> {

	@Override
	public boolean isValid(ItemSearchRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		boolean isValid = true;
		if ((request.startDate() == null) != (request.endDate() == null)) {
			isValid = ItemInputs.reject(context, request.startDate() == null ? "startDate" : "endDate",
				"시작일과 종료일을 함께 보내 주세요.");
		} else if (request.startDate() != null && request.startDate().isAfter(request.endDate())) {
			isValid = ItemInputs.reject(context, "endDate", "종료일은 시작일보다 빠를 수 없습니다.");
		}
		if (request.minPrice() != null && request.maxPrice() != null && request.minPrice() > request.maxPrice()) {
			isValid = ItemInputs.reject(context, "minPrice", "최소 금액은 최대 금액보다 클 수 없습니다.");
		}
		return isValid;
	}
}
