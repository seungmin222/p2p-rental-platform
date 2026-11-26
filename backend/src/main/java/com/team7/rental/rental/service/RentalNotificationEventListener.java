package com.team7.rental.rental.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.global.config.NotificationListenerExecutor;
import com.team7.rental.rental.event.RentalNotificationEvent;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link RentalNotificationPublisher}가 남긴 알림을 커밋 뒤에 보낸다. 포트 구현은 예외를 던지지 않지만, 계약이 깨져도 커밋된 상태 전이의
 * 응답이 실패로 보이지 않게 여기서도 잡고 로그만 남긴다.
 * <p>
 * 보내기는 {@link NotificationListenerExecutor}에서 한다. 요청 스레드가 원래 트랜잭션의 커넥션과 물품 락을 쥔 채 알림 저장용 두 번째 커넥션을
 * 기다리면 풀이 교착되기 때문이다(docs/experiments/load-test.md "병목").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalNotificationEventListener {

	private final RentalNotificationPort rentalNotificationPort;
	private final NotificationListenerExecutor notificationListenerExecutor;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNotification(RentalNotificationEvent event) {
		notificationListenerExecutor.execute(describe(event), () -> send(event));
	}

	private void send(RentalNotificationEvent event) {
		try {
			rentalNotificationPort.send(event.memberId(), event.type(), TargetType.RENTAL, event.rentalId(),
				event.eventId());
		} catch (RuntimeException e) {
			log.warn("Rental notification failed after commit: {}, error={}", describe(event), e.toString());
		}
	}

	private static String describe(RentalNotificationEvent event) {
		return "memberId=" + event.memberId() + ", type=" + event.type() + ", rentalId=" + event.rentalId()
			+ ", eventId=" + event.eventId();
	}
}
