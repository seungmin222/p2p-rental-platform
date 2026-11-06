package com.team7.rental.rental.service;

import java.time.LocalDate;

/**
 * 대여 흐름이 부르는 알림 발행 (REQ-031, notification.md). 알림 저장·실시간 전달은 알림 도메인(REQ-032) 구현이 맡고 rental은 그쪽을 직접 알지 않는다.
 * 032 머지 전 운영 빈은 로그만 남기는 {@link LoggingRentalNotificationPort}다.
 * <p>
 * 계약:
 * <ul>
 *   <li>호출하는 쪽은 상태 전이 트랜잭션이 커밋된 뒤에 부른다(notification.md AFTER_COMMIT).</li>
 *   <li><b>중복 방지는 구현이 책임진다.</b> 같은 eventId(notifications.event_id UK)로 다시 불리면 저장하지 않고 예외 없이 무시한다.
 *   배치가 같은 대상을 다시 보는 경우(반납 리마인더 재실행 등) 호출하는 쪽은 같은 eventId를 넘길 뿐 중복을 따로 막지 않는다.</li>
 *   <li>발행 실패는 던지지 않는다(DB 저장이 기준, 실시간 전달 실패는 재전송하지 않는다).</li>
 * </ul>
 * 표시 문구(content)는 구현이 type으로 만든다.
 */
public interface RentalNotificationPort {

	/** state-machines.md §5 NotificationType 중 rental이 발행하는 것. 이름이 같으므로 구현은 name()으로 매핑한다 */
	enum Type {
		OVERDUE, RETURN_REMINDER
	}

	/** notifications.target_type 중 rental이 쓰는 것 (클릭 시 대여 상세로 이동) */
	enum TargetType {
		RENTAL
	}

	/**
	 * @param memberId 받는 회원 (notifications.member_id)
	 * @param targetId 이동할 대상 ID (notifications.target_id)
	 * @param eventId 중복 방지 키 (notifications.event_id). {@link #overdueEventId}·{@link #returnReminderEventId}로 만든다
	 */
	void send(Long memberId, Type type, TargetType targetType, Long targetId, String eventId);

	/** 연체 알림 키 {@code OVERDUE:{rentalId}}. 매시 재검사에도 1회만 (notification.md REQ-031) */
	static String overdueEventId(Long rentalId) {
		return Type.OVERDUE.name() + ":" + rentalId;
	}

	/**
	 * 반납 리마인더 키 {@code RETURN_REMINDER:{rentalId}:{endDate}}. 연장(REQ-038)으로 endDate가 바뀌면 다시 보낼 수 있게 종료일을 넣는다
	 * (rental.md REQ-023). endDate는 ISO 형식(yyyy-MM-dd)
	 */
	static String returnReminderEventId(Long rentalId, LocalDate endDate) {
		return Type.RETURN_REMINDER.name() + ":" + rentalId + ":" + endDate;
	}
}
