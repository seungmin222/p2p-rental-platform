package com.team7.rental.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.chat.dto.ChatRoomCreateResponse;
import com.team7.rental.chat.entity.ChatRoom;
import com.team7.rental.chat.entity.ChatRoomMember;
import com.team7.rental.chat.repository.ChatRoomMemberRepository;
import com.team7.rental.chat.repository.ChatRoomRepository;
import com.team7.rental.item.service.ItemChatCountWriter;

import lombok.RequiredArgsConstructor;

/**
 * 채팅방 insert 트랜잭션 (REQ-026). {@link ChatRoomService}가 UK 위반을 잡아 기존 방을 다시 읽을 수 있도록 별도 빈이다.
 * UK 위반이면 방·참여자·chat_count 증가가 모두 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class ChatRoomTxService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ItemChatCountWriter itemChatCountWriter;

	/**
	 * chat_count를 먼저 올려 물품 행 X 락을 잡은 뒤 방을 넣는다. 순서를 바꾸면(방 insert → 카운트) 동시 요청들이 FK 검사로 물품 행
	 * S 락을 쥔 채 UK 대기에 들어가고, 먼저 넣은 쪽이 카운트 X 락을 기다려 교착(1213)이 난다. 지금 순서에서는 같은 물품의 생성이 물품 행
	 * 락에서 줄을 서고, 늦은 쪽은 UK 위반으로 롤백된다(올린 카운트도 함께 되돌아간다).
	 */
	@Transactional
	public ChatRoomCreateResponse open(Long itemId, Long renterId, Long ownerId) {
		itemChatCountWriter.increase(itemId);
		ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.open(itemId, renterId, ownerId));
		chatRoomMemberRepository.saveAll(List.of(ChatRoomMember.join(room.getId(), renterId),
			ChatRoomMember.join(room.getId(), ownerId)));
		return ChatRoomCreateResponse.of(room);
	}
}
