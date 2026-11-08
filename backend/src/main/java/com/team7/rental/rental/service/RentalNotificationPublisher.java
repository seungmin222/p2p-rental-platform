package com.team7.rental.rental.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.event.RentalNotificationEvent;
import com.team7.rental.rental.service.RentalNotificationPort.Type;

import lombok.RequiredArgsConstructor;

/**
 * 상태 전이 트랜잭션 안에서 알림을 남긴다 (REQ-032 수신자 매핑, notification.md). 이벤트만 남기고 커밋 뒤
 * {@link RentalNotificationEventListener}가 보낸다. <b>쓰기 트랜잭션 안에서만 부른다</b>(트랜잭션이 없으면 리스너가 돌지 않는다).
 * 수신자 규칙(대여자·제공자·양측·상대방)은 이 클래스의 메서드 이름으로 드러낸다.
 */
@Component
@RequiredArgsConstructor
public class RentalNotificationPublisher {

	private final ApplicationEventPublisher eventPublisher;

	public void toRenter(Rental rental, Type type, String eventId) {
		publish(rental.getRenterId(), type, rental, eventId);
	}

	public void toOwner(Rental rental, Type type, String eventId) {
		publish(rental.getOwnerId(), type, rental, eventId);
	}

	/** 대여자·제공자 둘 다, 같은 eventId (회원마다 한 번씩 저장된다, open-issues #49) */
	public void toBoth(Rental rental, Type type, String eventId) {
		toRenter(rental, type, eventId);
		toOwner(rental, type, eventId);
	}

	/** 행동한 당사자의 상대방 */
	public void toCounterpart(Rental rental, Long actorId, Type type, String eventId) {
		publish(actorId.equals(rental.getRenterId()) ? rental.getOwnerId() : rental.getRenterId(), type, rental,
			eventId);
	}

	private void publish(Long memberId, Type type, Rental rental, String eventId) {
		eventPublisher.publishEvent(new RentalNotificationEvent(memberId, type, rental.getId(), eventId));
	}
}
