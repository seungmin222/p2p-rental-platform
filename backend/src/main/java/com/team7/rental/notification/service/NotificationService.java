package com.team7.rental.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.notification.dto.NotificationReadAllResponse;
import com.team7.rental.notification.dto.NotificationResponse;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 발행의 단일 진입점과 읽음 처리 (REQ-032). 다른 도메인은 알림을 직접 만들지 않고 {@link #send}(또는 그것을 부르는 포트 구현)로만 보낸다
 * (notification.md 설계 결정).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationTxService notificationTxService;
	private final NotificationPushPort notificationPushPort;
	private final NotificationRepository notificationRepository;
	private final Clock clock;

	/**
	 * 알림 하나를 저장하고 실시간으로 보낸다. 호출하는 쪽은 상태 전이가 커밋된 뒤에 부른다(AFTER_COMMIT).
	 * <ul>
	 *   <li>같은 (memberId, eventId)는 한 번만 저장한다. 다시 오면 예외 없이 무시하고 실시간 전달도 하지 않는다.</li>
	 *   <li>예외를 던지지 않는다. 저장이 실패하면 로그만 남긴다(DB 저장이 기준, 실패한 알림은 다시 보내지 않는다).</li>
	 *   <li>실시간 전달은 저장이 커밋된 뒤에 한다. 실패해도 저장은 그대로다.</li>
	 * </ul>
	 *
	 * @return 새로 저장했으면 true
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public boolean send(Long memberId, NotificationType type, NotificationTargetType targetType, Long targetId,
		String eventId) {
		if (!isEnabled(memberId, type)) {
			return false;
		}
		Optional<NotificationResponse> saved;
		try {
			saved = notificationTxService.insertIfAbsent(memberId, type, targetType, targetId, eventId);
		} catch (DataIntegrityViolationException e) {
			// 같은 키가 동시에 들어와 늦은 쪽이 유니크 위반이면 중복이다. 아니면(FK 등) 저장 실패
			if (!isDuplicate(memberId, eventId)) {
				log.warn("Notification save failed: memberId={}, type={}, eventId={}, error={}", memberId, type,
					eventId, e.toString());
			}
			return false;
		} catch (RuntimeException e) {
			log.warn("Notification save failed: memberId={}, type={}, eventId={}, error={}", memberId, type, eventId,
				e.toString());
			return false;
		}
		saved.ifPresent(notification -> push(memberId, notification));
		return saved.isPresent();
	}

	/**
	 * 알림 설정(REQ-053, 3순위) 자리. 구현 전에는 모든 종류를 보낸다. 053 구현 때 notification_settings에서 수신 거부한 종류면 false를 돌려준다.
	 */
	private boolean isEnabled(Long memberId, NotificationType type) {
		return true;
	}

	private boolean isDuplicate(Long memberId, String eventId) {
		try {
			return notificationTxService.exists(memberId, eventId);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void push(Long memberId, NotificationResponse notification) {
		try {
			notificationPushPort.push(memberId, notification);
		} catch (RuntimeException e) {
			log.warn("Notification push failed (saved): memberId={}, notificationId={}, error={}", memberId,
				notification.notificationId(), e.toString());
		}
	}

	/**
	 * 개별 읽음. 이미 읽은 알림도 성공(멱등).
	 *
	 * @throws BusinessException NOTIFICATION_NOT_FOUND: 없는 알림, NOTIFICATION_NOT_OWNER: 다른 회원의 알림
	 */
	@Transactional
	public void markRead(Long memberId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		if (!notification.getMemberId().equals(memberId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_NOT_OWNER);
		}
		notification.markRead();
	}

	/** 모두 읽음 */
	@Transactional
	public NotificationReadAllResponse markAllRead(Long memberId) {
		return new NotificationReadAllResponse(notificationRepository.markAllRead(memberId, LocalDateTime.now(clock)));
	}
}
