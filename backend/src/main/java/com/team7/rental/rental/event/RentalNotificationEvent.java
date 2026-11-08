package com.team7.rental.rental.event;

import com.team7.rental.rental.service.RentalNotificationPort.Type;

/**
 * 상태 전이 트랜잭션 안에서 남긴 알림 한 건. 커밋 뒤 {@code RentalNotificationEventListener}가 {@code RentalNotificationPort}로 보낸다.
 * 롤백되면 보내지 않는다(notification.md AFTER_COMMIT).
 *
 * @param memberId 받는 회원
 * @param eventId 중복 방지 키 ({@code RentalNotificationPort}의 static 메서드로 만든다)
 */
public record RentalNotificationEvent(Long memberId, Type type, Long rentalId, String eventId) {
}
