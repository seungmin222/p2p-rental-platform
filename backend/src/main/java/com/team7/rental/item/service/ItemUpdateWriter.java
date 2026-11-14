package com.team7.rental.item.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemPeriodRequest;
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemAvailablePeriod;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.rental.service.RentalOccupancyReader;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;

import lombok.RequiredArgsConstructor;

/**
 * 물품 수정의 트랜잭션 부분(REQ-007). ItemService가 물품 락(lock:item:{itemId}) 안에서 호출한다. 별도 빈이어야 트랜잭션 프록시가 걸리고
 * 커밋이 락 해제보다 먼저 일어난다. 새 이미지 파일은 호출하는 쪽이 미리 올리고, 빠진 기존 이미지 파일은 커밋 뒤 호출하는 쪽이 지운다.
 */
@Service
@RequiredArgsConstructor
public class ItemUpdateWriter {

	/** 점유 기간 조회 범위. 이미 시작된 대여(과거 날짜)도 검사하므로 MySQL DATE 범위 전체를 본다 */
	private static final LocalDate OCCUPANCY_FROM = LocalDate.of(1000, 1, 1);
	private static final LocalDate OCCUPANCY_TO = LocalDate.of(9999, 12, 31);

	private final ItemQueryService itemQueryService;
	private final CategoryService categoryService;
	private final ItemImageRepository itemImageRepository;
	private final ItemAvailablePeriodRepository itemAvailablePeriodRepository;
	private final RentalOccupancyReader rentalOccupancyReader;
	private final Clock clock;

	public record UploadedImage(String url, String thumbnailUrl) {
	}

	/**
	 * @param removedImages 이번 수정으로 빠진 기존 이미지. 호출하는 쪽이 커밋 뒤 파일을 지운다
	 */
	public record Result(ItemEditResponse response, List<ItemImage> removedImages) {
	}

	/**
	 * 락 밖 검사(404·403·카테고리·imageId)를 락 안에서 다시 한다: 락을 기다리는 동안 삭제·다른 수정이 커밋됐을 수 있다.
	 *
	 * @param uploaded images 파트 순번(newImageIndex) 순서의 새 이미지
	 * @throws BusinessException ITEM_NOT_FOUND, ITEM_NOT_OWNER, COMMON_INVALID_INPUT(imageOrder·categoryId),
	 *     ITEM_PERIOD_CONFLICT
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Result update(Long itemId, Long memberId, ItemUpdateRequest request, List<UploadedImage> uploaded) {
		Item item = itemQueryService.getOwnedItem(itemId, memberId);
		if (request.categoryId() != null) {
			categoryService.requireActive(request.categoryId());
		}
		List<ItemImage> currentImages = itemImageRepository.findAllByItemIdOrderBySortOrderAscIdAsc(itemId);
		if (request.imageOrder() != null) {
			validateImageIds(request.imageOrder(), currentImages);
		}
		if (request.availablePeriods() != null) {
			validatePeriodsCoverOccupied(itemId, request.availablePeriods());
		}

		item.update(request.name(), request.description(), request.categoryId(), request.condition(),
			request.pricePerDay(), request.deposit(), request.itemPrice(), request.address(), request.latitude(),
			request.longitude());
		if (request.availablePeriods() != null) {
			itemAvailablePeriodRepository.deleteAllByItemId(itemId);
			for (ItemPeriodRequest period : request.availablePeriods()) {
				itemAvailablePeriodRepository.save(
					ItemAvailablePeriod.create(itemId, period.startDate(), period.endDate()));
			}
		}
		List<ItemImage> removed = request.imageOrder() == null
			? List.of()
			: replaceImages(itemId, request.imageOrder(), currentImages, uploaded);
		return new Result(itemQueryService.toEditResponse(item), removed);
	}

	/**
	 * imageOrder의 imageId가 모두 이 물품의 현재 이미지인지.
	 *
	 * @throws BusinessException COMMON_INVALID_INPUT(details imageOrder)
	 */
	static void validateImageIds(List<ItemUpdateRequest.ImageOrder> imageOrder, List<ItemImage> currentImages) {
		Set<Long> currentIds = currentImages.stream().map(ItemImage::getId).collect(Collectors.toSet());
		boolean isAllCurrent = imageOrder.stream()
			.map(ItemUpdateRequest.ImageOrder::imageId)
			.filter(Objects::nonNull)
			.allMatch(currentIds::contains);
		if (!isAllCurrent) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("imageOrder", "이 물품의 이미지가 아닌 imageId가 있습니다.")));
		}
	}

	// 점유 기간의 날짜 중 새 가능 기간들 어디에도 들지 않는 날짜가 있으면 그 점유 기간을 충돌로 모은다
	private void validatePeriodsCoverOccupied(Long itemId, List<ItemPeriodRequest> periods) {
		LocalDateTime now = LocalDateTime.now(clock);
		List<FieldErrorDetail> conflicts = new ArrayList<>();
		for (OccupiedPeriod occupied : rentalOccupancyReader.findOccupied(itemId, OCCUPANCY_FROM, OCCUPANCY_TO,
			now)) {
			boolean isCovered = occupied.startDate().datesUntil(occupied.endDate().plusDays(1))
				.allMatch(date -> periods.stream()
					.anyMatch(period -> !date.isBefore(period.startDate()) && !date.isAfter(period.endDate())));
			if (!isCovered) {
				conflicts.add(
					new FieldErrorDetail("availablePeriods", occupied.startDate() + " ~ " + occupied.endDate()));
			}
		}
		if (!conflicts.isEmpty()) {
			throw new BusinessException(ErrorCode.ITEM_PERIOD_CONFLICT, conflicts);
		}
	}

	// 목록 순서 = sortOrder. 유지할 기존 이미지는 순서만 바꾸고, 새 이미지는 추가하고, 빠진 기존 이미지 행은 지운다
	private List<ItemImage> replaceImages(Long itemId, List<ItemUpdateRequest.ImageOrder> imageOrder,
		List<ItemImage> currentImages, List<UploadedImage> uploaded) {
		Map<Long, ItemImage> remaining = currentImages.stream()
			.collect(Collectors.toMap(ItemImage::getId, Function.identity()));
		for (int sortOrder = 0; sortOrder < imageOrder.size(); sortOrder++) {
			ItemUpdateRequest.ImageOrder slot = imageOrder.get(sortOrder);
			if (slot.imageId() != null) {
				remaining.remove(slot.imageId()).changeSortOrder(sortOrder);
			} else {
				UploadedImage image = uploaded.get(slot.newImageIndex());
				itemImageRepository.save(ItemImage.create(itemId, image.url(), image.thumbnailUrl(), sortOrder));
			}
		}
		List<ItemImage> removed = List.copyOf(remaining.values());
		itemImageRepository.deleteAll(removed);
		return removed;
	}
}
