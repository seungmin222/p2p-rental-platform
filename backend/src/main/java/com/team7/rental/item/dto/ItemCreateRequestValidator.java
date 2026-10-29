package com.team7.rental.item.dto;

import java.time.Clock;
import java.time.LocalDate;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** {@link ValidItemInput} — 등록: 글자 수·금액 범위(item.*)와 대여 가능 기간(시작일 ≥ 오늘 포함) */
@RequiredArgsConstructor
public class ItemCreateRequestValidator implements ConstraintValidator<ValidItemInput, ItemCreateRequest> {

	private final PolicyProperties policyProperties;
	private final Clock clock;

	@Override
	public boolean isValid(ItemCreateRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		PolicyProperties.Item policy = policyProperties.item();
		boolean isValid = ItemInputs.checkText(request.name(), policy.nameMax(), "name", context);
		isValid &= ItemInputs.checkText(request.description(), policy.descriptionMax(), "description", context);
		isValid &= ItemInputs.checkAmount(request.pricePerDay(), policy.pricePerDay(), "pricePerDay", context);
		isValid &= ItemInputs.checkAmount(request.deposit(), policy.deposit(), "deposit", context);
		isValid &= ItemInputs.checkItemPrice(request.itemPrice(), policy.itemPriceMax(), "itemPrice", context);
		isValid &= ItemInputs.checkPeriods(request.availablePeriods(), LocalDate.now(clock), context);
		return isValid;
	}
}
