package com.team7.rental.chat.dto;

import java.time.LocalDateTime;

/**
 * {@code /user/queue/chat-rooms} 목록 갱신 이벤트 (REQ-027). 새 메시지를 받은 쪽(상대방)에게 간다.
 * 목록 화면(REQ-028)은 이 값으로 해당 방을 맨 위로 올리고 미리보기를 바꾼다. 안 읽은 수는 029에서 더한다.
 */
public record ChatRoomUpdateResponse(Long roomId, Long lastMessageId, String lastMessagePreview,
	LocalDateTime lastMessageAt) {

	public static ChatRoomUpdateResponse of(ChatMessageResponse message) {
		return new ChatRoomUpdateResponse(message.roomId(), message.messageId(), message.content(), message.sentAt());
	}
}
