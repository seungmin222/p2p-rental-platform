package com.team7.rental.chat.dto;

import java.time.LocalDateTime;

/**
 * {@code /user/queue/chat-rooms} 목록 갱신 이벤트 (REQ-027·029). 새 메시지를 받은 쪽(상대방)에게 간다.
 * 목록 화면(REQ-028)은 이 값으로 해당 방을 맨 위로 올리고 미리보기·안 읽은 수를 바꾸고, 하단 탭은 totalUnreadCount로 배지를 바꾼다.
 *
 * @param unreadCount 받는 회원의 이 방 안 읽은 수(전송 커밋 직후 기준)
 * @param totalUnreadCount 받는 회원의 전체 안 읽은 수(전송 커밋 직후 기준)
 */
public record ChatRoomUpdateResponse(Long roomId, Long lastMessageId, String lastMessagePreview,
	LocalDateTime lastMessageAt, long unreadCount, long totalUnreadCount) {

	public static ChatRoomUpdateResponse of(ChatMessageResponse message, long unreadCount, long totalUnreadCount) {
		return new ChatRoomUpdateResponse(message.roomId(), message.messageId(), message.content(), message.sentAt(),
			unreadCount, totalUnreadCount);
	}
}
