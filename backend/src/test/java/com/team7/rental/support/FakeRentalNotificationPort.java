package com.team7.rental.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.team7.rental.rental.service.RentalNotificationPort;

/**
 * 로그 구현 대신 쓰는 알림 포트. 발행 요청을 순서대로 기록만 한다(중복 제거 없음: 호출 횟수와 event_id를 그대로 검증하려고).
 * 각 테스트가 끝나면 {@link IntegrationTestSupport}가 비운다.
 */
@Primary
@TestComponent
public class FakeRentalNotificationPort implements RentalNotificationPort {

	public record Sent(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
	}

	private final List<Sent> sent = new ArrayList<>();

	@Override
	public synchronized void send(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
		sent.add(new Sent(memberId, type, targetType, targetId, eventId));
	}

	public synchronized List<Sent> sent() {
		return List.copyOf(sent);
	}

	public synchronized void reset() {
		sent.clear();
	}
}
