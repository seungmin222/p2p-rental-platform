package com.team7.rental.rental.service;

import java.time.LocalDate;

/**
 * 대여 흐름이 부르는 알림 발행 (REQ-031·049, notification.md). 보증금 보류·차감(payment 도메인)도 이 포트를 쓴다(payment → rental 방향). 알림 저장·실시간 전달은 알림 도메인(REQ-032) 구현이 맡고 rental은 그쪽을 직접 알지 않는다.
 * 구현은 알림 도메인의 {@code RentalNotificationAdapter}다(notifications 저장, 실시간 전달은 채팅 구현 뒤).
 * 상태 전이 트랜잭션 안에서 알릴 때는 {@link RentalNotificationPublisher}로 이벤트를 남기면 커밋 뒤 이 포트로 보낸다.
 * <p>
 * 계약:
 * <ul>
 *   <li>호출하는 쪽은 상태 전이 트랜잭션이 커밋된 뒤에 부른다(notification.md AFTER_COMMIT).</li>
 *   <li><b>중복 방지는 구현이 책임진다.</b> 같은 회원에게 같은 eventId(notifications (member_id, event_id) UK, open-issues #49)로 다시 불리면 저장하지 않고 예외 없이 무시한다.
 *   배치가 같은 대상을 다시 보는 경우(반납 리마인더 재실행 등) 호출하는 쪽은 같은 eventId를 넘길 뿐 중복을 따로 막지 않는다.</li>
 *   <li>발행 실패는 던지지 않는다(DB 저장이 기준, 실시간 전달 실패는 재전송하지 않는다).</li>
 * </ul>
 * 표시 문구(content)는 구현이 type으로 만든다.
 */
public interface RentalNotificationPort {

	/** state-machines.md §5 NotificationType 중 rental이 발행하는 것. 이름이 같으므로 구현은 name()으로 매핑한다 */
	enum Type {
		RESERVATION_CONFIRMED,
		PAYMENT_COMPLETED,
		RESERVATION_CANCELED,
		CANCEL_AGREEMENT_REQUESTED,
		CANCEL_AGREEMENT_RESULT,
		HANDOVER_CONFIRMED,
		RETURN_REQUESTED,
		INSPECTION_RESULT,
		/** 보증금 반환 PG 환불 성공 (REQ-025). 반환액 0원(FORFEITED)은 보내지 않는다 */
		DEPOSIT_REFUNDED,
		OVERDUE,
		RETURN_REMINDER,
		LOST_REPORTED,
		DEPOSIT_HELD,
		DEDUCTION_PROPOSED,
		DEDUCTION_ACCEPTED,
		DEDUCTION_REJECTED,
		RENTAL_EXTENDED,
		/** AI 상태 비교 완료(REQ-040). ai 도메인이 발행한다(ai → rental 방향). 양측에 같은 eventId (OVERDUE와 같음) */
		AI_COMPARE_DONE
	}

	/** notifications.target_type 중 rental이 쓰는 것 (클릭 시 대여 상세로 이동) */
	enum TargetType {
		RENTAL
	}

	/**
	 * @param memberId 받는 회원 (notifications.member_id)
	 * @param targetId 이동할 대상 ID (notifications.target_id)
	 * @param eventId 중복 방지 키 (notifications.event_id, member_id와 함께 유니크). {@link #overdueEventId}·{@link #returnReminderEventId}로 만든다
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

	/** 한 대여에 한 번인 알림 키 {@code {TYPE}:{rentalId}} (예약 확정·결제 완료·예약 취소·인계·반납 요청·상태 확인·보증금 반환·보류, 미반납 신고, AI 비교) */
	static String rentalEventId(Type type, Long rentalId) {
		return type.name() + ":" + rentalId;
	}

	/** 연장 확정 알림 키 {@code RENTAL_EXTENDED:{rentalId}:{extensionId}} (REQ-038). 연장은 대여당 여러 번이라 연장 ID로 구분한다 */
	static String extensionEventId(Long rentalId, Long extensionId) {
		return Type.RENTAL_EXTENDED.name() + ":" + rentalId + ":" + extensionId;
	}

	/** 양측 동의 취소 요청별 알림 키 {@code {TYPE}:{rentalId}:{agreementId}} (CANCEL_AGREEMENT_REQUESTED·RESULT). 거절 뒤 다시 요청할 수 있어 요청 ID로 구분한다 */
	static String cancelAgreementEventId(Type type, Long rentalId, Long agreementId) {
		return type.name() + ":" + rentalId + ":" + agreementId;
	}

	/** 차감 제안별 알림 키 {@code {TYPE}:{rentalId}:{proposalId}} (notification.md event_id 규칙) */
	static String deductionEventId(Type type, Long rentalId, Long proposalId) {
		return type.name() + ":" + rentalId + ":" + proposalId;
	}
}
