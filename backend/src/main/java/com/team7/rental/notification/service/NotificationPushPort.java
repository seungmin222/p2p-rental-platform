package com.team7.rental.notification.service;

import com.team7.rental.notification.dto.NotificationResponse;

/**
 * 실시간 전달 (notification.md 설계 결정: 채팅과 같은 STOMP 연결의 개인 큐 {@code /user/queue/notifications}).
 * DB 저장이 기준이고 이 경로는 부가다. 실패해도 재전송하지 않는다(재접속하면 목록 API로 받는다).
 * <p>
 * STOMP 설정은 채팅(REQ-026~029) 브랜치가 만든다. 그 전까지 운영 빈은 아무것도 하지 않는 {@link NoopNotificationPushPort}다.
 * 구현은 예외를 던지지 않는다.
 */
public interface NotificationPushPort {

	/** 새로 저장된 알림 하나를 받는 회원에게 보낸다 */
	void push(Long memberId, NotificationResponse notification);
}
