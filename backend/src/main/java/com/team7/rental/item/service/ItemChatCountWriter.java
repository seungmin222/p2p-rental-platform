package com.team7.rental.item.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

/** 물품 채팅 수(chat_count) 갱신 창구 (REQ-026). 채팅방 생성 트랜잭션에 참여한다 */
@Service
@RequiredArgsConstructor
public class ItemChatCountWriter {

	private final ItemRepository itemRepository;

	@Transactional
	public void increase(Long itemId) {
		itemRepository.increaseChatCount(itemId);
	}
}
