package com.team7.rental.notification.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.notification.dto.NotificationResponse;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 알림 저장 트랜잭션. 항상 새 트랜잭션이다(REQUIRES_NEW). 발행 지점은 대부분 AFTER_COMMIT 리스너라, 끝난 원래 트랜잭션에 참여하면
 * 쓰기가 커밋되지 않기 때문이다. 저장 실패가 원래 작업을 되돌리지 않는 것도 이 분리 덕분이다.
 */
@Service
@RequiredArgsConstructor
public class NotificationTxService {

	private final NotificationRepository notificationRepository;

	/**
	 * 먼저 있는지 보고 없으면 INSERT한다. 같은 키가 동시에 들어오면 늦은 쪽은 커밋 때 유니크 위반이 나고, 호출하는 쪽이
	 * {@link #exists}로 중복인지 확인한다.
	 * <p>
	 * MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE}는 쓰지 않는다. Connector/J 기본 설정(found rows)에서는 중복도 1행으로 세어
	 * 저장 여부를 알 수 없다. INSERT IGNORE는 유니크 위반 말고 다른 오류(FK·길이 초과)까지 경고로 삼킨다.
	 *
	 * @return 새로 저장했으면 그 알림, 같은 (memberId, eventId)가 이미 있으면 비어 있다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationResponse> insertIfAbsent(Long memberId, NotificationType type,
		NotificationTargetType targetType, Long targetId, String eventId) {
		if (notificationRepository.existsByMemberIdAndEventId(memberId, eventId)) {
			return Optional.empty();
		}
		Notification saved = notificationRepository.saveAndFlush(Notification.create(memberId, type,
			NotificationContentTemplate.contentOf(type), targetType, targetId, eventId));
		return Optional.of(NotificationResponse.from(saved));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public boolean exists(Long memberId, String eventId) {
		return notificationRepository.existsByMemberIdAndEventId(memberId, eventId);
	}
}
