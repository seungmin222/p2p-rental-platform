package com.team7.rental.chat.dto;

/**
 * {@code /user/queue/chat-reads} 읽음 이벤트 (REQ-029). 읽은 사람의 상대에게 간다.
 * 받은 쪽은 이 값 이하인 내 메시지를 '읽음'으로 표시한다.
 *
 * @param memberId 읽은 회원
 * @param lastReadMessageId 읽은 회원의 새 읽음 위치
 */
public record ChatReadResponse(Long roomId, Long memberId, Long lastReadMessageId) {
}
