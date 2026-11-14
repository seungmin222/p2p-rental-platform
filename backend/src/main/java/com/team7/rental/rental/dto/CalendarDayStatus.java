package com.team7.rental.rental.dto;

/** 캘린더 날짜 상태 (REQ-012) */
public enum CalendarDayStatus {
	/** 예약 가능 */
	AVAILABLE,
	/** 기간 점유 대여(ACTIVE_FOR_PERIOD, 결제 대기는 만료 전, 종료일 지난 RENTING·OVERDUE는 오늘까지)가 덮는 날짜 */
	BOOKED,
	/** 지난 날짜 또는 대여 가능 기간 밖 */
	UNAVAILABLE
}
