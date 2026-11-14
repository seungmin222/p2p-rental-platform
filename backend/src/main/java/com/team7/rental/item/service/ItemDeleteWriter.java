package com.team7.rental.item.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.item.entity.Item;
import com.team7.rental.rental.service.RentalDeletionBlockReader;

import lombok.RequiredArgsConstructor;

/**
 * 물품 삭제의 트랜잭션 부분(REQ-008). ItemService가 물품 락(lock:item:{itemId}) 안에서 호출한다(open-issues #27).
 * 이미지 파일은 지우지 않는다: 지난 대여·채팅이 삭제된 물품의 대표 이미지를 계속 보여 준다.
 */
@Service
@RequiredArgsConstructor
public class ItemDeleteWriter {

	private final ItemQueryService itemQueryService;
	private final RentalDeletionBlockReader rentalDeletionBlockReader;

	/**
	 * 락 밖 검사(404·403)를 락 안에서 다시 한다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·이미 삭제), ITEM_NOT_OWNER, ITEM_HAS_ACTIVE_RENTAL
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void delete(Long itemId, Long memberId) {
		Item item = itemQueryService.getOwnedItem(itemId, memberId);
		List<FieldErrorDetail> blocking = rentalDeletionBlockReader.findBlocking(itemId).stream()
			.map(rental -> new FieldErrorDetail("rentals", rental.describe()))
			.toList();
		if (!blocking.isEmpty()) {
			throw new BusinessException(ErrorCode.ITEM_HAS_ACTIVE_RENTAL, blocking);
		}
		item.delete();
	}
}
