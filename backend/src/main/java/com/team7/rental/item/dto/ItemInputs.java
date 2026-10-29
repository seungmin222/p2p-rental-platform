package com.team7.rental.item.dto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidatorContext;

/** {@link ValidItemInput} 검증기 공용. 각 check는 위반을 추가하고 false를 돌려준다(null은 통과 — 필수 여부는 필드 어노테이션이 본다) */
final class ItemInputs {

	private ItemInputs() {}

	static boolean checkText(String text, int max, String field, ConstraintValidatorContext context) {
		if (text != null && text.codePointCount(0, text.length()) > max) {
			return reject(context, field, max + "자 이하로 입력해 주세요.");
		}
		return true;
	}

	/** 0원 또는 [min, max] (item.price-per-day, item.deposit) */
	static boolean checkAmount(Integer amount, PolicyProperties.Item.Amount range, String field,
		ConstraintValidatorContext context) {
		if (amount != null && !range.isAllowed(amount)) {
			return reject(context, field, "0원 또는 " + range.min() + "원 이상 " + range.max() + "원 이하로 입력해 주세요.");
		}
		return true;
	}

	/** 0원 ~ item.item-price.max */
	static boolean checkItemPrice(Integer itemPrice, int max, String field, ConstraintValidatorContext context) {
		if (itemPrice != null && (itemPrice < 0 || itemPrice > max)) {
			return reject(context, field, "0원 이상 " + max + "원 이하로 입력해 주세요.");
		}
		return true;
	}

	/**
	 * 대여 가능 기간: 각 구간 시작일 ≤ 종료일, 구간끼리 겹침 불가(양 끝 포함), earliestStart가 있으면 시작일 ≥ earliestStart.
	 * 날짜가 빠진 구간은 원소의 @NotNull이 보므로 건너뛴다.
	 */
	static boolean checkPeriods(List<ItemPeriodRequest> periods, LocalDate earliestStart,
		ConstraintValidatorContext context) {
		if (periods == null) {
			return true;
		}
		List<ItemPeriodRequest> complete = periods.stream()
			.filter(Objects::nonNull)
			.filter(period -> period.startDate() != null && period.endDate() != null)
			.toList();
		boolean isValid = true;
		for (ItemPeriodRequest period : complete) {
			if (period.startDate().isAfter(period.endDate())) {
				isValid = reject(context, "availablePeriods", "시작일은 종료일보다 늦을 수 없습니다: " + period.display());
			} else if (earliestStart != null && period.startDate().isBefore(earliestStart)) {
				isValid = reject(context, "availablePeriods", "시작일은 오늘 이후여야 합니다: " + period.display());
			}
		}
		List<ItemPeriodRequest> sorted = complete.stream()
			.sorted(Comparator.comparing(ItemPeriodRequest::startDate))
			.toList();
		for (int i = 1; i < sorted.size(); i++) {
			if (!sorted.get(i).startDate().isAfter(sorted.get(i - 1).endDate())) {
				isValid = reject(context, "availablePeriods",
					"기간이 겹칩니다: " + sorted.get(i - 1).display() + ", " + sorted.get(i).display());
			}
		}
		return isValid;
	}

	static boolean reject(ConstraintValidatorContext context, String field, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
		return false;
	}
}
