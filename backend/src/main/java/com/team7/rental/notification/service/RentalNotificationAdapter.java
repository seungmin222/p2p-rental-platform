package com.team7.rental.notification.service;

import org.springframework.stereotype.Component;

import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.rental.service.RentalNotificationPort;

import lombok.RequiredArgsConstructor;

/**
 * 대여·결제·AI 흐름의 알림 발행 포트 구현 (REQ-031·032). rental이 정의한 포트를 알림 도메인이 구현해 rental이 알림 도메인을 모르게 한다
 * (RentalRefundPort를 결제 도메인이 구현하는 것과 같다). 포트 enum과 알림 enum은 이름이 같아 name()으로 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class RentalNotificationAdapter implements RentalNotificationPort {

	private final NotificationService notificationService;

	@Override
	public void send(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
		notificationService.send(memberId, NotificationType.valueOf(type.name()),
			NotificationTargetType.valueOf(targetType.name()), targetId, eventId);
	}
}
