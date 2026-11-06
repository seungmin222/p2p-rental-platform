package com.team7.rental.rental.service;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 도메인(REQ-032) 머지 전 임시 구현. 발행 내용을 로그로만 남긴다.
 * 032에서 {@link RentalNotificationPort}의 실제 구현(저장 + WebSocket)을 넣을 때 이 클래스를 삭제한다.
 */
@Slf4j
@Component
public class LoggingRentalNotificationPort implements RentalNotificationPort {

	@Override
	public void send(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
		log.info("Notification (not stored until REQ-032): memberId={}, type={}, target={}:{}, eventId={}", memberId,
			type, targetType, targetId, eventId);
	}
}
