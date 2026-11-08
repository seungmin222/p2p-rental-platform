package com.team7.rental.rental.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.rental.event.RentalNotificationEvent;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;

import lombok.RequiredArgsConstructor;

/** {@link RentalNotificationPublisher}가 남긴 알림을 커밋 뒤에 보낸다. 포트 구현은 예외를 던지지 않는다 */
@Component
@RequiredArgsConstructor
public class RentalNotificationEventListener {

	private final RentalNotificationPort rentalNotificationPort;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotification(RentalNotificationEvent event) {
		rentalNotificationPort.send(event.memberId(), event.type(), TargetType.RENTAL, event.rentalId(),
			event.eventId());
	}
}
