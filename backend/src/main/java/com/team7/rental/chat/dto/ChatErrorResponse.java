package com.team7.rental.chat.dto;

import com.team7.rental.global.response.ErrorResponse;

/**
 * {@code /user/queue/errors} 회신 (REQ-027). STOMP SEND가 서버에서 거부되면 보낸 세션에만 간다(연결은 유지).
 *
 * @param clientMessageId 실패한 SEND의 임시 ID. 본문을 읽지 못했으면 null
 * @param error REST 실패 응답의 error와 같은 형태
 */
public record ChatErrorResponse(String clientMessageId, ErrorResponse error) {
}
