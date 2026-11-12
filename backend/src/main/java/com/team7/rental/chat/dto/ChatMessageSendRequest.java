package com.team7.rental.chat.dto;

import com.team7.rental.chat.entity.ChatMessageType;

/**
 * STOMP SEND {@code /pub/chat-rooms/{roomId}} 본문 (REQ-027). 검증은 서비스가 한다: type은 TEXT만, content는 공백만 불가·
 * policies.md chat.message.max(1,000자) 이하.
 *
 * @param clientMessageId 클라이언트가 낙관적 표시에 쓴 임시 ID. 서버는 저장하지 않고 브로드캐스트·오류 회신에 그대로 돌려준다
 */
public record ChatMessageSendRequest(ChatMessageType type, String content, String clientMessageId) {
}
