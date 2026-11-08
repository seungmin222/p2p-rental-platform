package com.team7.rental.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UnreadCountResponse(
	@Schema(description = "안 읽은 알림 수 (상단 알림 아이콘 배지)", example = "3")
	long count) {
}
