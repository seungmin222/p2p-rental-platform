package com.team7.rental.chat.event;

import com.team7.rental.chat.dto.ChatMessageResponse;

/** 메시지 저장 커밋 뒤 STOMP 전파용 (REQ-027) */
public record ChatMessageSentEvent(ChatMessageResponse message, Long recipientId) {
}
