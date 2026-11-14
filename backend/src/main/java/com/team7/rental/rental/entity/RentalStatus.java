package com.team7.rental.rental.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** 대여 상태 (docs/state-machines.md §1). 전이는 Rental의 업무 메서드로만 한다. */
public enum RentalStatus {
	PENDING_PAYMENT, RESERVED, RENTING, OVERDUE, RETURNED, COMPLETED, CANCELED;

	/**
	 * 기간을 점유하는 상태. 예약 겹침 검사(REQ-013)·캘린더(REQ-012)·대여 가능 기간 수정 검사(REQ-007)가 같이 쓴다.
	 * <p>
	 * PENDING_PAYMENT는 {@code payment_expires_at > now}일 때만 점유한다. 이 조건은 Set으로 표현할 수 없으므로 쿼리는
	 * 만료 조건을 따로 걸고, 엔티티 하나는 {@link Rental#isOccupyingPeriod}로 판단한다.
	 */
	public static final Set<RentalStatus> ACTIVE_FOR_PERIOD = Collections.unmodifiableSet(
		EnumSet.of(PENDING_PAYMENT, RESERVED, RENTING, OVERDUE));

	/**
	 * 물품 삭제(REQ-008)를 막는 상태. 결제 대기는 만료 여부와 상관없이 막는다(만료 배치가 CANCELED로 바꾸면 풀린다).
	 * RETURNED는 상태 확인·보증금 처리가 남아 있어 막는다.
	 */
	public static final Set<RentalStatus> BLOCKING_ITEM_DELETION = Collections.unmodifiableSet(
		EnumSet.of(PENDING_PAYMENT, RESERVED, RENTING, OVERDUE, RETURNED));

	/** 화면 표시 이름 (state-machines.md §1, RETURNED는 화면 배지와 같게 '상태 확인 대기') */
	public String displayName() {
		return switch (this) {
			case PENDING_PAYMENT -> "결제 대기";
			case RESERVED -> "대여 예정";
			case RENTING -> "대여 중";
			case OVERDUE -> "연체";
			case RETURNED -> "상태 확인 대기";
			case COMPLETED -> "거래 완료";
			case CANCELED -> "취소";
		};
	}
}
