package com.team7.rental.rental.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.rental.event.RentalNotificationEvent;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RentalNotificationPublisher}가 남긴 알림을 커밋 뒤에 보낸다. 포트 구현은 예외를 던지지 않지만, 계약이 깨져도 커밋된 상태 전이의
 * 응답이 실패로 보이지 않게 여기서도 잡고 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalNotificationEventListener {

	private final RentalNotificationPort rentalNotificationPort;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotification(RentalNotificationEvent event) {
		try {
			rentalNotificationPort.send(event.memberId(), event.type(), TargetType.RENTAL, event.rentalId(),
				event.eventId());
		} catch (RuntimeException e) {
			log.warn("Rental notification failed after commit: memberId={}, type={}, rentalId={}, eventId={}, error={}",
				event.memberId(), event.type(), event.rentalId(), event.eventId(), e.toString());
		}
	}
}
