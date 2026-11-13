package com.team7.rental.chat.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.chat.dto.ChatReadRequest;
import com.team7.rental.chat.dto.ChatReadResponse;
import com.team7.rental.chat.entity.ChatRoom;
import com.team7.rental.chat.entity.ChatRoomMember;
import com.team7.rental.chat.event.ChatReadEvent;
import com.team7.rental.chat.repository.ChatMessageRepository;
import com.team7.rental.chat.repository.ChatRoomMemberRepository;
import com.team7.rental.chat.repository.ChatRoomRepository;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;

import lombok.RequiredArgsConstructor;

/**
 * 읽음 처리 (REQ-029): STOMP {@code /pub/chat-rooms/{id}/read}. 내 chat_room_members 행을 잠그고 읽음 위치를 앞으로만 옮긴 뒤,
 * 바뀌었으면 커밋 뒤 상대에게 {@code /user/queue/chat-reads}로 알린다({@link ChatBroadcastListener}).
 */
@Service
@RequiredArgsConstructor
public class ChatReadService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * lastMessageId가 지금 읽음 위치 이하면 아무것도 하지 않는다(이벤트도 없음).
	 *
	 * @throws BusinessException COMMON_INVALID_INPUT(lastMessageId 없음·그 방 메시지가 아님), CHAT_ROOM_NOT_FOUND,
	 *     CHAT_NOT_PARTICIPANT
	 */
	@Transactional
	public void markRead(Long memberId, Long roomId, ChatReadRequest request) {
		Long messageId = request == null ? null : request.lastMessageId();
		if (messageId == null || messageId <= 0) {
			throw invalid("메시지 ID를 보내 주세요.");
		}
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT);
		}
		// 다른 방이나 아직 없는 큰 id를 받으면 앞으로 올 메시지까지 읽음 처리되므로 이 방에 저장된 메시지만 받는다
		if (!chatMessageRepository.existsByIdAndRoomId(messageId, roomId)) {
			throw invalid("이 채팅방의 메시지가 아닙니다.");
		}
		ChatRoomMember me = chatRoomMemberRepository.findWithLock(roomId, memberId)
			.orElseThrow(() -> new IllegalStateException("참여자 행이 없습니다: roomId=" + roomId + ", memberId=" + memberId));
		if (me.markRead(messageId)) {
			eventPublisher.publishEvent(
				new ChatReadEvent(new ChatReadResponse(roomId, memberId, messageId), room.counterpartOf(memberId)));
		}
	}

	private BusinessException invalid(String reason) {
		return new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
			List.of(new FieldErrorDetail("lastMessageId", reason)));
	}
}
