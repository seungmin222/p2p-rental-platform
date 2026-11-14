package com.team7.rental.item.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.item.dto.ItemDepositGuideRequest;
import com.team7.rental.item.dto.ItemDepositGuideResponse;
import com.team7.rental.item.dto.ItemDetailResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemFeedRequest;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.item.dto.ItemSearchRequest;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.dto.ItemSummaryResponse;
import com.team7.rental.item.dto.MyItemRequest;
import com.team7.rental.item.dto.MyItemResponse;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/**
 * 물품 조회. 화면용 조회(REQ-006 권장 보증금·009 피드·010 검색·011 상세·020 내 물품, 아래쪽 미구현 메서드)와
 * 다른 도메인(rental 등)이 물품 정보를 읽는 창구(대여 예약·캘린더·상세·빌린 내역 REQ-012·013·016·019)를 겸한다.
 * 엔티티 대신 item/dto의 record를 반환한다.
 */
@lombok.extern.slf4j.Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemQueryService {

	private final ItemRepository itemRepository;
	private final CategoryService categoryService;
	private final com.team7.rental.payment.service.DamagePolicyQueryService damagePolicies;
	private final jakarta.validation.Validator validator;
	private final java.time.Clock clock;
	private final com.team7.rental.global.policy.PolicyProperties policies;
	private final com.team7.rental.member.service.MemberQueryService members;
	private final com.team7.rental.member.service.RegionService regions;
	private final com.team7.rental.item.repository.CategoryRepository categories;
	private final com.team7.rental.item.repository.ItemViewRepository views;
	private final ItemAvailablePeriodRepository itemAvailablePeriodRepository;
	private final ItemImageRepository itemImageRepository;

	/** 권장 보증금 범위 (REQ-006) */
	public ItemDepositGuideResponse getDepositGuide(ItemDepositGuideRequest request) {
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT, violations.stream()
				.map(v -> new com.team7.rental.global.response.FieldErrorDetail(v.getPropertyPath().toString(),
					v.getMessage()))
				.toList());
		}
		categoryService.requireActive(request.categoryId());
		var result = damagePolicies.recommend(request.categoryId(), request.itemPrice());
		return new ItemDepositGuideResponse(result.minDeposit(), result.maxDeposit());
	}

	/** 동네 피드 (REQ-009). regionId가 없으면 회원의 대표 동네 */
	public CursorResponse<ItemSummaryResponse> getFeed(ItemFeedRequest request, Long memberId) {
		Long regionId = request.regionId();
		if (regionId == null && memberId != null) {
			var primary = members.getMe(memberId).primaryRegion();
			regionId = primary == null ? null : primary.regionId();
		}
		if (regionId == null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new com.team7.rental.global.response.FieldErrorDetail("regionId", "기준 동네를 선택해 주세요.")));
		}
		var cursor = com.team7.rental.item.repository.ItemFeedCursor.decode(request.cursor());
		var rows = itemRepository.findFeed(regions.getCenter(regionId), policies.region().range().normalMeters(),
			request.categoryId(),
			cursor, policies.feed().page() + 1, java.time.LocalDateTime.now(clock));
		boolean hasNext = rows.size() > policies.feed().page();
		var page = rows.subList(0, Math.min(rows.size(), policies.feed().page()));
		var names = regions
			.getNames(page.stream().map(com.team7.rental.item.repository.ItemListRow::regionId).distinct().toList());
		var thumbnails = thumbnails(page);
		var content = page.stream().map(row -> new ItemSummaryResponse(row.itemId(), row.name(),
			thumbnails.get(row.itemId()), names.get(row.regionId()), row.pricePerDay(), row.deposit(),
			row.wishCount(), row.chatCount(), row.renting(), row.bumpedAt())).toList();
		String next = hasNext ? new com.team7.rental.item.repository.ItemFeedCursor(page.getLast().bumpedAt(),
			page.getLast().itemId()).encode() : null;
		return new CursorResponse<>(content, next, hasNext);
	}

	/** 필터링 검색 (REQ-010). regionId가 없으면 회원의 대표 동네 */
	public PageResponse<ItemSummaryResponse> search(ItemSearchRequest request, Long memberId) {
		throw new UnsupportedOperationException("미구현: feat/item-feed-search");
	}

	/** 상세 (REQ-011). memberId는 비회원이면 null */
	public ItemDetailResponse getDetail(Long itemId, Long memberId, String remoteAddress) {
		Item item = getVisibleItem(itemId, memberId);
		var category = categories.findById(item.getCategoryId())
			.orElseThrow(() -> new IllegalStateException("카테고리 참조 오류"));
		var owner = members.getPublicSummary(item.getOwnerId());
		String regionName = regions.getName(item.getRegionId());
		var images = itemImageRepository.findAllByItemIdOrderBySortOrderAscIdAsc(itemId).stream()
			.map(image -> new com.team7.rental.item.dto.ItemImageResponse(image.getId(), image.getUrl(),
				image.getThumbnailUrl()))
			.toList();
		int viewCount = item.getViewCount();
		try {
			viewCount = views.record(itemId, memberId, remoteAddress, viewCount);
		} catch (org.springframework.dao.DataAccessException | org.redisson.client.RedisException failure) {
			// 부가 집계 장애가 상세 조회를 막지 않게 한다. IP/회원 정보는 로그에 남기지 않는다.
			log.warn("조회수 Redis 집계 실패: itemId={}, cause={}", itemId, failure.getClass().getSimpleName());
		}
		return new ItemDetailResponse(item.getId(), item.getName(), item.getDescription(), images,
			new ItemDetailResponse.Category(category.getId(), category.getName()), item.getItemCondition(),
			item.getPricePerDay(), item.getDeposit(), regionName, item.getAddress(), item.getLatitude(),
			item.getLongitude(), item.getStatus(), item.getBumpedAt(), viewCount, item.getWishCount(),
			item.getChatCount(), new ItemDetailResponse.Owner(owner.memberId(), owner.nickname(),
				owner.profileImageUrl(), owner.regionName()),
			memberId != null && item.isOwnedBy(memberId), false);
	}

	/**
	 * 소유자 수정 화면 (REQ-007). itemPrice 포함. 숨김 물품도 허용한다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·삭제), ITEM_NOT_OWNER
	 */
	public ItemEditResponse getEditView(Long itemId, Long memberId) {
		return toEditResponse(getOwnedItem(itemId, memberId));
	}

	/**
	 * 소유자가 수정·삭제할 물품. 숨김 물품도 허용한다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·삭제), ITEM_NOT_OWNER
	 */
	Item getOwnedItem(Long itemId, Long memberId) {
		Item item = itemRepository.findById(itemId).filter(value -> !value.isDeleted())
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		if (!item.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.ITEM_NOT_OWNER);
		}
		return item;
	}

	/** 물품의 현재 이미지 (sortOrder 순). 수정 요청의 imageId 검사용 */
	List<ItemImage> getImages(Long itemId) {
		return itemImageRepository.findAllByItemIdOrderBySortOrderAscIdAsc(itemId);
	}

	/** 수정 화면 조회와 수정 응답이 같이 쓴다. 기간은 시작일 순, 이미지는 sortOrder 순 */
	ItemEditResponse toEditResponse(Item item) {
		var periods = itemAvailablePeriodRepository.findAllByItemIdOrderByStartDate(item.getId()).stream()
			.map(period -> new ItemEditResponse.Period(period.getStartDate(), period.getEndDate()))
			.toList();
		var images = itemImageRepository.findAllByItemIdOrderBySortOrderAscIdAsc(item.getId()).stream()
			.map(image -> new com.team7.rental.item.dto.ItemImageResponse(image.getId(), image.getUrl(),
				image.getThumbnailUrl()))
			.toList();
		return new ItemEditResponse(item.getId(), item.getName(), item.getDescription(), item.getCategoryId(),
			item.getItemCondition(), item.getPricePerDay(), item.getItemPrice(), item.getDeposit(), periods, images,
			item.getAddress(), item.getLatitude(), item.getLongitude(), item.getStatus());
	}

	/** 내 물품 관리 (REQ-020) */
	public PageResponse<MyItemResponse> getMyItems(Long memberId, MyItemRequest request) {
		var rows = itemRepository.findMyItems(memberId, request.tab(),
			org.springframework.data.domain.PageRequest.of(request.page(), policies.myItems().page()),
			java.time.LocalDateTime.now(clock));
		var names = regions
			.getNames(rows.stream().map(com.team7.rental.item.repository.ItemListRow::regionId).distinct().toList());
		var thumbnails = thumbnails(rows.getContent());
		return PageResponse.of(rows, row -> new MyItemResponse(row.itemId(), row.name(), thumbnails.get(row.itemId()),
			names.get(row.regionId()), row.pricePerDay(), row.deposit(), row.status(), row.viewCount(), row.wishCount(),
			row.chatCount(), Math.toIntExact(row.upcomingCount()), row.renting(), row.bumpedAt()));
	}

	private Map<Long, String> thumbnails(List<com.team7.rental.item.repository.ItemListRow> rows) {
		Map<Long, String> result = new HashMap<>();
		if (!rows.isEmpty()) {
			for (var image : itemImageRepository
				.findMainImages(rows.stream().map(com.team7.rental.item.repository.ItemListRow::itemId).toList())) {
				result.putIfAbsent(image.getItemId(), image.getThumbnailUrl());
			}
		}
		return result;
	}

	/** 소유자 전용 목록은 숨김 물품도 허용한다. 없는 물품/삭제는 404, 타인 소유는 403. */
	public void validateOwner(Long itemId, Long memberId) {
		var item = itemRepository.findById(itemId).filter(value -> value.getStatus() != ItemStatus.DELETED)
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		if (!item.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.ITEM_NOT_OWNER);
		}
	}

	/**
	 * 채팅방 생성 대상 물품의 소유자 (REQ-026). 숨김(HIDDEN) 물품도 허용한다(이미 대화 중인 상대와 같은 규칙). 없음·삭제는 404.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public Long getChatOwnerId(Long itemId) {
		return itemRepository.findById(itemId).filter(value -> value.getStatus() != ItemStatus.DELETED)
			.map(Item::getOwnerId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
	}

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
	 * 물품의 현재 카테고리 (보증금 차감 상한 계산, REQ-049). 삭제된 물품도 반환한다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public Long getCategoryId(Long itemId) {
		return itemRepository.findById(itemId)
			.map(Item::getCategoryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
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
