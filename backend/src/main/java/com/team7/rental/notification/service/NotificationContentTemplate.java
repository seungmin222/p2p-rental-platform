package com.team7.rental.notification.service;

import com.team7.rental.notification.entity.NotificationType;

/**
 * 알림 표시 문구(notifications.content). type마다 고정 문구 하나다.
 * <p>
 * <b>임시 문구다.</b> 명세(notification.md)는 문구를 정하지 않았다. 기획이 문구를 정하면 이 파일만 바꾼다(open-issues #50).
 * 물품명 같은 인자가 필요해지면 발행 포트에 args를 더한다(notification.md 설계 결정의 {@code send(..., args)}).
 * 이미 저장된 알림의 문구는 바뀌지 않는다.
 */
final class NotificationContentTemplate {

	private NotificationContentTemplate() {}

	static String contentOf(NotificationType type) {
		return switch (type) {
			case RESERVATION_CONFIRMED -> "예약이 확정됐어요.";
			case PAYMENT_COMPLETED -> "결제가 완료돼 예약이 확정됐어요.";
			case RESERVATION_CANCELED -> "예약이 취소됐어요.";
			case CANCEL_AGREEMENT_REQUESTED -> "상대방이 예약 취소 동의를 요청했어요.";
			case CANCEL_AGREEMENT_RESULT -> "예약 취소 동의 요청이 처리됐어요.";
			case HANDOVER_CONFIRMED -> "물품 인계가 확인됐어요. 대여가 시작됐어요.";
			case HANDOVER_REMINDER -> "아직 물품 인계 확인이 없어요.";
			case RETURN_REMINDER -> "내일이 반납 예정일이에요.";
			case RETURN_REQUESTED -> "대여자가 반납을 요청했어요. 물품 상태를 확인해 주세요.";
			case LOST_REPORTED -> "제공자가 미반납을 신고했어요.";
			case INSPECTION_RESULT -> "반납 물품의 상태 확인 결과가 나왔어요.";
			case DEPOSIT_REFUNDED -> "보증금이 반환됐어요.";
			case DEPOSIT_HELD -> "보증금이 보류됐어요.";
			case DEDUCTION_PROPOSED -> "보증금 차감 제안이 도착했어요.";
			case DEDUCTION_ACCEPTED -> "보증금 차감이 확정됐어요.";
			case DEDUCTION_REJECTED -> "대여자가 보증금 차감 제안을 거절했어요.";
			case OVERDUE -> "반납 예정일이 지났어요.";
			case RENTAL_EXTENDED -> "대여 기간이 연장됐어요.";
			case REVIEW_RECEIVED -> "새 후기가 도착했어요.";
			case AI_COMPARE_DONE -> "AI 상태 비교 결과가 나왔어요.";
			case KEYWORD_MATCHED -> "관심 키워드에 맞는 물품이 등록됐어요.";
			case APPOINTMENT_REMINDER -> "약속 시간이 다가와요.";
		};
	}
}
