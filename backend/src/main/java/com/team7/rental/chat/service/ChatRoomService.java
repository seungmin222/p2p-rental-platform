package com.team7.rental.chat.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.team7.rental.chat.dto.ChatRoomCreateResponse;
import com.team7.rental.chat.dto.ChatRoomCreateResult;
import com.team7.rental.chat.entity.ChatRoom;
import com.team7.rental.chat.repository.ChatRoomRepository;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.service.ItemQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 채팅방 생성·참여자 확인 (REQ-026). 클래스에 트랜잭션을 걸지 않는다: 동시 생성으로 UK 위반이 나면 상대 트랜잭션이 커밋한 방을
 * 새로 읽어야 해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

	private final ItemQueryService itemQueryService;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomTxService chatRoomTxService;

	/**
	 * (물품, 요청자) 방이 있으면 그 방, 없으면 새로 만든다. 새로 만들 때만 물품 chat_count +1.
	 *
	 * @throws BusinessException ITEM_NOT_FOUND(없음·삭제), CHAT_OWN_ITEM
	 */
	public ChatRoomCreateResult create(Long memberId, Long itemId) {
		Long ownerId = itemQueryService.getChatOwnerId(itemId);
		if (ownerId.equals(memberId)) {
			throw new BusinessException(ErrorCode.CHAT_OWN_ITEM);
		}
		var existing = chatRoomRepository.findByItemIdAndRenterId(itemId, memberId);
		if (existing.isPresent()) {
			return new ChatRoomCreateResult(ChatRoomCreateResponse.of(existing.get()), false);
		}
		try {
			return new ChatRoomCreateResult(chatRoomTxService.open(itemId, memberId, ownerId), true);
		} catch (DataIntegrityViolationException e) {
			// 같은 (물품, 대여자) 동시 생성: 먼저 커밋된 방을 돌려준다. 다른 제약 위반이면 방이 없으므로 원래 예외를 던진다
			ChatRoom room = chatRoomRepository.findByItemIdAndRenterId(itemId, memberId).orElseThrow(() -> e);
			log.info("Chat room create race resolved: roomId={}", room.getId());
			return new ChatRoomCreateResult(ChatRoomCreateResponse.of(room), false);
		}
	}

	/**
	 * STOMP SUBSCRIBE 인가 (REQ-027).
	 *
	 * @throws BusinessException CHAT_ROOM_NOT_FOUND, CHAT_NOT_PARTICIPANT
	 */
	public void validateParticipant(Long roomId, Long memberId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT);
		}
	}
}
