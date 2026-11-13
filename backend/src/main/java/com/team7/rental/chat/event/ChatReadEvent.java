package com.team7.rental.chat.event;

import com.team7.rental.chat.dto.ChatReadResponse;

/** 읽음 위치 갱신 커밋 뒤 상대에게 알리기용 (REQ-029) */
public record ChatReadEvent(ChatReadResponse read, Long recipientId) {
}
