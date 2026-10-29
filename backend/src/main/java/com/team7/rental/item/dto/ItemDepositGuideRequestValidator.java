package com.team7.rental.item.dto;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidItemInput} — 권장 보증금 조회의 물품 가격 범위(item.item-price.max) */
@RequiredArgsConstructor
public class ItemDepositGuideRequestValidator implements ConstraintValidator<ValidItemInput, ItemDepositGuideRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(ItemDepositGuideRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		return ItemInputs.checkItemPrice(request.itemPrice(), policyProperties.item().itemPriceMax(), "itemPrice",
			context);
	}
}
