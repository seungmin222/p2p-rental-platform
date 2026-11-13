package com.team7.rental.chat.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅 목록 항목 (REQ-028·029) */
public record ChatRoomSummaryResponse(
	@Schema(description = "채팅방 ID", example = "3")
	Long roomId,

	ChatCounterpartResponse counterpart,

	ChatItemResponse item,

	@Schema(description = "마지막 메시지 내용(최대 chat.message.max자, 자르기는 화면). 메시지가 없으면 null", nullable = true)
	String lastMessagePreview,

	@Schema(description = "마지막 메시지 시각. 메시지가 없으면 null", nullable = true)
	LocalDateTime lastMessageAt,

	@Schema(description = "이 방에서 내가 안 읽은 메시지 수(상대가 보낸 것과 SYSTEM 메시지)", example = "2")
	long unreadCount) {
}
