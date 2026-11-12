package com.team7.rental.notification.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.team7.rental.chat.service.ChatDestinations;
import com.team7.rental.notification.dto.NotificationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 실시간 전달 (REQ-032): 받는 회원의 {@code /user/queue/notifications}. 접속해 있지 않으면 아무 데도 가지 않는다.
 * 서버가 여러 대여도 브로커 릴레이의 user destination broadcast로 다른 서버에 붙은 세션까지 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompNotificationPushPort implements NotificationPushPort {

	private final SimpMessagingTemplate messagingTemplate;

	@Override
	public void push(Long memberId, NotificationResponse notification) {
		try {
			messagingTemplate.convertAndSendToUser(memberId.toString(), ChatDestinations.NOTIFICATIONS_QUEUE,
				notification);
		} catch (RuntimeException e) {
			log.warn("Notification STOMP push failed: memberId={}, notificationId={}, error={}", memberId,
				notification.notificationId(), e.toString());
		}
	}
}
