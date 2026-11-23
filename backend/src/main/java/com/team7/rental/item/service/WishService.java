package com.team7.rental.item.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.item.dto.WishResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 관심(찜) 추가·해제 (REQ-022). 추가·해제 모두 멱등이다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishService {

	private final WishTxService wishTxService;

	/**
	 * 물품 행 락으로 같은 물품의 추가가 줄을 서므로 UK 위반은 보통 나지 않는다. 그래도 나면(락 밖에서 행이 생긴 경우) 롤백된 뒤 한 번 다시
	 * 시도해 이미 담긴 상태로 응답한다. 다시 시도해도 실패하면(FK 등 다른 제약) 원래 예외를 던진다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND, WISH_OWN_ITEM
	 */
	public WishResponse add(Long memberId, Long itemId) {
		try {
			return wishTxService.add(memberId, itemId);
		} catch (DataIntegrityViolationException e) {
			log.info("Wish add race retried: itemId={}", itemId);
			return wishTxService.add(memberId, itemId);
		}
	}

	/** @throws BusinessException ITEM_NOT_FOUND */
	public WishResponse remove(Long memberId, Long itemId) {
		return wishTxService.remove(memberId, itemId);
	}
}
