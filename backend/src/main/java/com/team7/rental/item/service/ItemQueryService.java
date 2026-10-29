package com.team7.rental.item.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 다른 도메인(rental 등)이 물품 정보를 읽는 창구. 엔티티 대신 item/dto의 record를 반환한다.
 * 지금은 대여 예약·캘린더·상세·빌린 내역(REQ-012·013·016·019)에 필요한 메서드만 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemQueryService {

	private final ItemRepository itemRepository;
	private final ItemAvailablePeriodRepository itemAvailablePeriodRepository;
	private final ItemImageRepository itemImageRepository;

	/**
	 * 예약 대상 물품. 공개 여부는 {@link #validateVisible}과 같은 규칙이다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public ItemRentalTarget getRentalTarget(Long itemId, Long requesterId) {
		Item item = getVisibleItem(itemId, requesterId);
		return new ItemRentalTarget(item.getId(), item.getOwnerId(), item.getPricePerDay(), item.getDeposit());
	}

	/**
	 * 상세 조회(REQ-011)와 같은 공개 규칙: 없음·DELETED는 404, HIDDEN은 소유자만(그 외 404).
	 *
	 * @param requesterId 비회원이면 null
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public void validateVisible(Long itemId, Long requesterId) {
		getVisibleItem(itemId, requesterId);
	}

	/** 시작일 순 */
	public List<AvailablePeriod> getAvailablePeriods(Long itemId) {
		return itemAvailablePeriodRepository.findAllByItemIdOrderByStartDate(itemId).stream()
			.map(period -> new AvailablePeriod(period.getStartDate(), period.getEndDate()))
			.toList();
	}

	/**
	 * 삭제된 물품도 반환한다(지난 대여 화면에서 쓰므로).
	 *
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public ItemSummary getSummary(Long itemId) {
		Item item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		String thumbnailUrl = itemImageRepository.findFirstByItemIdOrderBySortOrder(itemId)
			.map(ItemImage::getThumbnailUrl)
			.orElse(null);
		return new ItemSummary(item.getId(), item.getName(), thumbnailUrl, item.getStatus() == ItemStatus.DELETED);
	}

	/**
	 * {@link #getSummary}의 일괄 조회판(목록 화면용). 규칙이 같아 삭제된 물품도 담는다. 쿼리는 물품 수와 상관없이 2개(물품·대표 이미지)다.
	 * 대표 이미지가 sort_order로 동률이면 id가 작은 것을 쓴다.
	 *
	 * @return 물품 ID → 요약. 없는 ID는 담지 않는다(없는 경우의 처리는 호출하는 쪽이 정한다)
	 */
	public Map<Long, ItemSummary> getSummaries(Collection<Long> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> thumbnailUrls = new HashMap<>();
		for (ItemImage image : itemImageRepository.findMainImages(itemIds)) {
			thumbnailUrls.putIfAbsent(image.getItemId(), image.getThumbnailUrl());
		}
		Map<Long, ItemSummary> summaries = new HashMap<>();
		for (Item item : itemRepository.findAllById(itemIds)) {
			summaries.put(item.getId(), new ItemSummary(item.getId(), item.getName(), thumbnailUrls.get(item.getId()),
				item.getStatus() == ItemStatus.DELETED));
		}
		return summaries;
	}

	private Item getVisibleItem(Long itemId, Long requesterId) {
		Item item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		boolean isVisible = switch (item.getStatus()) {
			case AVAILABLE -> true;
			case HIDDEN -> requesterId != null && item.isOwnedBy(requesterId);
			case DELETED -> false;
		};
		if (!isVisible) {
			throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
		}
		return item;
	}
}
