package com.team7.rental.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.team7.rental.notification.service.RentalNotificationAdapter;
import com.team7.rental.rental.service.RentalNotificationPort;

/**
 * 발행 요청을 순서대로 기록하고(중복 제거 없음: 호출 횟수와 event_id를 그대로 검증하려고) 실제 구현에도 넘긴다.
 * 그래서 통합 테스트에서 notifications 저장(같은 (회원, event_id)는 1행)도 함께 확인할 수 있다.
 * 각 테스트가 끝나면 {@link IntegrationTestSupport}가 기록을 비운다(저장된 행은 DataCleaner가 지운다).
 */
@Primary
@TestComponent
public class FakeRentalNotificationPort implements RentalNotificationPort {

	public record Sent(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
	}

	private final List<Sent> sent = new ArrayList<>();
	private final Set<Long> failingTargetIds = new HashSet<>();
	private final RentalNotificationAdapter delegate;

	public FakeRentalNotificationPort(RentalNotificationAdapter delegate) {
		this.delegate = delegate;
	}

	@Override
	public void send(Long memberId, Type type, TargetType targetType, Long targetId, String eventId) {
		synchronized (this) {
			if (failingTargetIds.contains(targetId)) {
				throw new IllegalStateException("Injected notification failure: targetId=" + targetId);
			}
			sent.add(new Sent(memberId, type, targetType, targetId, eventId));
		}
		delegate.send(memberId, type, targetType, targetId, eventId);
	}

	public synchronized List<Sent> sent() {
		return List.copyOf(sent);
	}

	/** 이 targetId로 오는 발행 요청은 기록·저장하지 않고 예외를 던진다(배치의 건별 실패 격리 검증용) */
	public synchronized void failOnTarget(Long targetId) {
		failingTargetIds.add(targetId);
	}

	public synchronized void reset() {
		sent.clear();
		failingTargetIds.clear();
	}
}
