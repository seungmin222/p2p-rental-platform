package com.team7.rental.chat.dto;

/**
 * STOMP SEND {@code /pub/chat-rooms/{roomId}/read} 본문 (REQ-029).
 *
 * @param lastMessageId 화면에 표시한 이 방의 가장 최근 메시지 ID. 그 방의 메시지여야 한다
 */
public record ChatReadRequest(Long lastMessageId) {
}
