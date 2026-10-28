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
}
