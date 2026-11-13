package com.team7.rental.item.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/** 물품 채팅 수(chat_count) 갱신 창구 (REQ-026). 채팅방 생성 트랜잭션에 참여한다 */
@Service
@RequiredArgsConstructor
public class ItemChatCountWriter {

	private final ItemRepository itemRepository;

	/**
	 * 삭제 확인(ItemQueryService.getChatOwnerId) 뒤에 물품이 삭제됐으면 여기서 거부해 방 생성 트랜잭션 전체를 롤백시킨다.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·삭제)
	 */
	@Transactional
	public void increase(Long itemId) {
		if (itemRepository.increaseChatCount(itemId) == 0) {
			throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
		}
	}
}
