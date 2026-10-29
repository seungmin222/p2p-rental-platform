package com.team7.rental.item.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.team7.rental.global.policy.PolicyProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/**
 * {@link ValidItemInput} — 수정: 보낸 값만 등록과 같은 범위로 검사한다. 보낸 문자열이 비었거나, 기간 목록이 비었거나,
 * 위도·경도 중 하나만 보냈거나, imageOrder 칸이 imageId·newImageIndex 중 정확히 하나가 아니거나 중복이면 위반.
 * 이미지 전체 장수(item.images)와 파일 파트와의 대응은 서비스가 본다.
 */
@RequiredArgsConstructor
public class ItemUpdateRequestValidator implements ConstraintValidator<ValidItemInput, ItemUpdateRequest> {

	private final PolicyProperties policyProperties;

	@Override
	public boolean isValid(ItemUpdateRequest request, ConstraintValidatorContext context) {
		if (request == null) {
			return true;
		}
		PolicyProperties.Item policy = policyProperties.item();
		boolean isValid = checkNotBlank(request.name(), "name", context);
		isValid &= checkNotBlank(request.description(), "description", context);
		isValid &= checkNotBlank(request.address(), "address", context);
		isValid &= ItemInputs.checkText(request.name(), policy.nameMax(), "name", context);
		isValid &= ItemInputs.checkText(request.description(), policy.descriptionMax(), "description", context);
		isValid &= ItemInputs.checkAmount(request.pricePerDay(), policy.pricePerDay(), "pricePerDay", context);
		isValid &= ItemInputs.checkAmount(request.deposit(), policy.deposit(), "deposit", context);
		isValid &= ItemInputs.checkItemPrice(request.itemPrice(), policy.itemPriceMax(), "itemPrice", context);
		if (request.availablePeriods() != null && request.availablePeriods().isEmpty()) {
			isValid = ItemInputs.reject(context, "availablePeriods", "1개 이상 입력해 주세요.");
		}
		isValid &= ItemInputs.checkPeriods(request.availablePeriods(), null, context);
		if ((request.latitude() == null) != (request.longitude() == null)) {
			isValid = ItemInputs.reject(context, "latitude", "위도와 경도는 함께 보내야 합니다.");
		}
		isValid &= checkImageOrder(request.imageOrder(), context);
		return isValid;
	}

	private boolean checkNotBlank(String text, String field, ConstraintValidatorContext context) {
		if (text != null && text.isBlank()) {
			return ItemInputs.reject(context, field, "공백일 수 없습니다.");
		}
		return true;
	}

	private boolean checkImageOrder(List<ItemUpdateRequest.ImageOrder> imageOrder,
		ConstraintValidatorContext context) {
		if (imageOrder == null) {
			return true;
		}
		Set<Long> imageIds = new HashSet<>();
		Set<Integer> newImageIndexes = new HashSet<>();
		for (ItemUpdateRequest.ImageOrder slot : imageOrder.stream().filter(Objects::nonNull).toList()) {
			if ((slot.imageId() == null) == (slot.newImageIndex() == null)) {
				return ItemInputs.reject(context, "imageOrder", "각 칸에는 imageId와 newImageIndex 중 하나만 넣어 주세요.");
			}
			boolean isDuplicate = slot.imageId() != null
				? !imageIds.add(slot.imageId())
				: !newImageIndexes.add(slot.newImageIndex());
			if (isDuplicate) {
				return ItemInputs.reject(context, "imageOrder", "같은 이미지를 두 번 넣을 수 없습니다.");
			}
		}
		return true;
	}
}
