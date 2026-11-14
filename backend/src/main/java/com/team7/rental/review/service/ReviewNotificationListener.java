package com.team7.rental.review.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.service.NotificationService;
import com.team7.rental.review.event.ReviewWrittenEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 후기를 받은 회원에게 REVIEW_RECEIVED 알림 (REQ-034). 누르면 그 대여 상세로 간다(target RENTAL): 받은 쪽도 거기서 후기를 쓸 수 있다.
 * 키는 "한 대여에 한 번인 알림" 규칙대로 REVIEW_RECEIVED:{rentalId}다(받는 회원은 한 대여에서 상대 한 명에게 한 번만 받는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewNotificationListener {

	private final NotificationService notificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReviewWritten(ReviewWrittenEvent event) {
		try {
			notificationService.send(event.targetId(), NotificationType.REVIEW_RECEIVED, NotificationTargetType.RENTAL,
				event.rentalId(), NotificationType.REVIEW_RECEIVED.name() + ":" + event.rentalId());
		} catch (RuntimeException e) {
			log.warn("Review notification failed after commit: reviewId={}, targetId={}, error={}", event.reviewId(),
				event.targetId(), e.toString());
		}
	}
}
