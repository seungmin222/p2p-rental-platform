package com.team7.rental.notification.service;

import com.team7.rental.notification.dto.NotificationResponse;

/**
 * 실시간 전달 (notification.md 설계 결정: 채팅과 같은 STOMP 연결의 개인 큐 {@code /user/queue/notifications}).
 * DB 저장이 기준이고 이 경로는 부가다. 실패해도 재전송하지 않는다(재접속하면 목록 API로 받는다).
 * <p>
 * 구현은 {@link StompNotificationPushPort}(채팅 STOMP 설정, REQ-027).
 * 구현은 예외를 던지지 않는다.
 */
public interface NotificationPushPort {

	/** 새로 저장된 알림 하나를 받는 회원에게 보낸다 */
	void push(Long memberId, NotificationResponse notification);
}
