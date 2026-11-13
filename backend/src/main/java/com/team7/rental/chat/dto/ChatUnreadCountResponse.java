package com.team7.rental.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 전체 안 읽은 채팅 수 (REQ-029, 하단 탭 배지) */
public record ChatUnreadCountResponse(
	@Schema(description = "내가 참여한 모든 방의 안 읽은 메시지 수 합", example = "5")
	long unreadCount) {
}
