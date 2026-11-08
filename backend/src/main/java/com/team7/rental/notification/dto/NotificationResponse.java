package com.team7.rental.notification.dto;

import java.time.LocalDateTime;

import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationResponse(
	@Schema(description = "알림 ID", example = "15")
	Long notificationId,

	@Schema(description = "알림 종류")
	NotificationType type,

	@Schema(description = "표시 문구", example = "반납 예정일이 지났어요.")
	String content,

	@Schema(description = "눌렀을 때 이동할 대상 종류. RENTAL이면 대여 상세")
	NotificationTargetType targetType,

	@Schema(description = "이동할 대상 ID", example = "123")
	Long targetId,

	@Schema(description = "읽었으면 true")
	boolean isRead,

	@Schema(description = "알림 시각", example = "2026-11-08T09:00:00")
	LocalDateTime createdAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getType(), notification.getContent(),
			notification.getTargetType(), notification.getTargetId(), notification.isRead(),
			notification.getCreatedAt());
	}
}
