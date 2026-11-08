package com.team7.rental.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationReadAllResponse(
	@Schema(description = "이번에 읽음으로 바꾼 알림 수", example = "3")
	int updatedCount) {
}
