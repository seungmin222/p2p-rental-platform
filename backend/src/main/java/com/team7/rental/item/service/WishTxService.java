package com.team7.rental.item.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.dto.WishResponse;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.Wish;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.item.repository.WishRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관심 추가·해제 트랜잭션 (REQ-022). {@link WishService}가 UK 위반을 잡아 다시 시도할 수 있도록 별도 빈이다.
 * <p>
 * 두 메서드 모두 물품 행을 먼저 잠근다(SELECT ... FOR UPDATE). 같은 물품의 관심 변경이 이 락에서 줄을 서므로, 락을 잡은 뒤 읽은
 * wish_count에 ±1 한 값이 곧 커밋될 값이고, 같은 회원의 추가·해제가 엇갈려도 교착이 나지 않는다. 카운트 자체는 문서대로 원자적
 * UPDATE(wish_count ± 1)로 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class WishTxService {

	private final ItemRepository itemRepository;
	private final WishRepository wishRepository;

	/**
	 * 이미 관심이면 아무것도 바꾸지 않고 현재 상태를 돌려준다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·삭제·남의 숨김 물품), WISH_OWN_ITEM
	 */
	@Transactional
	public WishResponse add(Long memberId, Long itemId) {
		Item item = itemRepository.findWithLockById(itemId)
			.filter(value -> value.isVisibleTo(memberId))
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		if (item.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.WISH_OWN_ITEM);
		}
		int wishCount = item.getWishCount();
		if (wishRepository.existsByMemberIdAndItemId(memberId, itemId)) {
			return new WishResponse(itemId, true, wishCount);
		}
		// 해제와 같은 순서(물품 락 → wishes → 카운트)
		wishRepository.saveAndFlush(Wish.of(memberId, itemId));
		itemRepository.increaseWishCount(itemId);
		// REQ-060 구현 시 적용: 커밋 뒤 추천 캐시 recommend:{memberId}를 무효화한다(domains/item.md).
		return new WishResponse(itemId, true, wishCount + 1);
	}

	/**
	 * 관심이 아니면 아무것도 바꾸지 않고 현재 상태를 돌려준다. 삭제·숨김 물품도 해제할 수 있다(관심 목록에 남은 항목을 지울 수 있어야 하므로).
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없는 물품)
	 */
	@Transactional
	public WishResponse remove(Long memberId, Long itemId) {
		Item item = itemRepository.findWithLockById(itemId)
			.orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
		int wishCount = item.getWishCount();
		if (wishRepository.deleteByMemberIdAndItemId(memberId, itemId) == 0) {
			return new WishResponse(itemId, false, wishCount);
		}
		boolean decreased = itemRepository.decreaseWishCount(itemId) == 1;
		return new WishResponse(itemId, false, decreased ? wishCount - 1 : wishCount);
	}
}
