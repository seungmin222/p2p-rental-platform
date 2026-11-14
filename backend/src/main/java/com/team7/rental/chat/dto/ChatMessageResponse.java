package com.team7.rental.chat.dto;

import java.time.LocalDateTime;

import com.team7.rental.chat.entity.ChatMessage;
import com.team7.rental.chat.entity.ChatMessageType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code /topic/chat-rooms.{roomId}} 브로드캐스트 본문 (REQ-027). 보낸 사람도 같은 토픽으로 받아 임시 메시지를 이것으로 바꾼다.
 *
 * @param senderId SYSTEM 메시지는 null
 * @param clientMessageId 보낸 쪽이 SEND에 넣은 임시 ID. 없으면 null
 */
public record ChatMessageResponse(Long messageId, Long roomId,
	@Schema(description = "보낸 회원 ID. SYSTEM 메시지는 null", nullable = true)
	Long senderId, ChatMessageType type, String content,
	LocalDateTime sentAt, String clientMessageId) {

	public static ChatMessageResponse of(ChatMessage message, String clientMessageId) {
		return new ChatMessageResponse(message.getId(), message.getRoomId(), message.getSenderId(), message.getType(),
			message.getContent(), message.getSentAt(), clientMessageId);
	}
}
