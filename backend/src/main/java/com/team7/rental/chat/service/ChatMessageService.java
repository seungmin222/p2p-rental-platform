package com.team7.rental.chat.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.chat.dto.ChatMessageResponse;
import com.team7.rental.chat.dto.ChatMessageSendRequest;
import com.team7.rental.chat.entity.ChatMessage;
import com.team7.rental.chat.entity.ChatMessageType;
import com.team7.rental.chat.entity.ChatRoom;
import com.team7.rental.chat.event.ChatMessageSentEvent;
import com.team7.rental.chat.repository.ChatMessageRepository;
import com.team7.rental.chat.repository.ChatRoomRepository;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;

import lombok.RequiredArgsConstructor;

/**
 * 메시지 송신 (REQ-027): 참여자 검증 → 저장 → 방의 마지막 메시지 갱신 → 커밋 뒤 전파({@link ChatBroadcastListener}).
 * DB 저장이 기준이다. 전파가 실패해도 재전송하지 않고, 클라이언트는 재연결 때 REST(REQ-028)로 빠진 메시지를 보충한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * @throws BusinessException COMMON_INVALID_INPUT(type이 TEXT가 아님, 내용 공백·길이 초과), CHAT_ROOM_NOT_FOUND,
	 *     CHAT_NOT_PARTICIPANT
	 */
	@Transactional
	public ChatMessageResponse send(Long memberId, Long roomId, ChatMessageSendRequest request) {
		validate(request);
		ChatRoom room = chatRoomRepository.findWithLockById(roomId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
		if (!room.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.CHAT_NOT_PARTICIPANT);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		ChatMessage message = chatMessageRepository.save(ChatMessage.text(roomId, memberId, request.content(), now));
		room.recordLastMessage(message.getContent(), now);
		ChatMessageResponse response = ChatMessageResponse.of(message, request.clientMessageId());
		eventPublisher.publishEvent(new ChatMessageSentEvent(response, room.counterpartOf(memberId)));
		return response;
	}

	private void validate(ChatMessageSendRequest request) {
		if (request.type() != ChatMessageType.TEXT) {
			throw invalid("type", "TEXT만 보낼 수 있습니다.");
		}
		String content = request.content();
		if (content == null || content.isBlank()) {
			throw invalid("content", "내용을 입력해 주세요.");
		}
		int max = policyProperties.chat().messageMax();
		if (content.codePointCount(0, content.length()) > max) {
			throw invalid("content", max + "자 이하로 입력해 주세요.");
		}
	}

	private BusinessException invalid(String field, String reason) {
		return new BusinessException(ErrorCode.COMMON_INVALID_INPUT, List.of(new FieldErrorDetail(field, reason)));
	}
}
