package com.team7.rental.notification.service;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.notification.dto.NotificationResponse;
import com.team7.rental.notification.dto.UnreadCountResponse;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.repository.NotificationCursor;
import com.team7.rental.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/** 알림 목록·안 읽은 수 (REQ-032) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationQueryService {

	private final NotificationRepository notificationRepository;
	private final PolicyProperties policyProperties;

	/**
	 * 내 알림 최신순, 한 번에 notification.page건 (size를 받지 않는다).
	 *
	 * @throws com.team7.rental.global.error.BusinessException COMMON_INVALID_INPUT: 해석할 수 없는 커서
	 */
	public CursorResponse<NotificationResponse> getNotifications(Long memberId, String cursor) {
		NotificationCursor decoded = NotificationCursor.decode(cursor);
		int size = policyProperties.notification().page();
		Limit limit = Limit.of(size + 1);
		List<Notification> rows = decoded == null
			? notificationRepository.findByMemberIdOrderByIdDesc(memberId, limit)
			: notificationRepository.findByMemberIdAndIdLessThanOrderByIdDesc(memberId, decoded.notificationId(),
				limit);
		boolean hasNext = rows.size() > size;
		List<Notification> page = rows.subList(0, Math.min(rows.size(), size));
		String next = hasNext ? new NotificationCursor(page.getLast().getId()).encode() : null;
		return new CursorResponse<>(page.stream().map(NotificationResponse::from).toList(), next, hasNext);
	}

	public UnreadCountResponse getUnreadCount(Long memberId) {
		return new UnreadCountResponse(notificationRepository.countByMemberIdAndIsReadFalse(memberId));
	}
}
