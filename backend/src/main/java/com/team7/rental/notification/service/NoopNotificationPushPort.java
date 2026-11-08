package com.team7.rental.notification.service;

import org.springframework.stereotype.Component;

import com.team7.rental.notification.dto.NotificationResponse;

/**
 * STOMP(채팅 REQ-026~029) 전 임시 구현. 아무것도 보내지 않는다. 채팅 브랜치가 {@code SimpMessagingTemplate#convertAndSendToUser}로
 * {@code /user/queue/notifications}에 보내는 구현을 넣을 때 이 클래스를 삭제한다.
 */
@Component
public class NoopNotificationPushPort implements NotificationPushPort {

	@Override
	public void push(Long memberId, NotificationResponse notification) {
		// 실시간 전달 없음. 목록·안 읽은 수 API로 확인한다
	}
}
