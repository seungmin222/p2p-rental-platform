package com.team7.rental.rental.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import lombok.Getter;

/** 빌린 내역(REQ-019) 탭. 포함 상태는 docs/state-machines.md "UI 탭 매핑" 표와 같다. */
@Getter
public enum RentalHistoryTab {
	/** 대여 예정. 결제 시간이 지난 PENDING_PAYMENT도 만료 배치(REQ-024)가 CANCELED로 바꾸기 전까지 여기에 그대로 보인다 */
	UPCOMING(EnumSet.of(RentalStatus.PENDING_PAYMENT, RentalStatus.RESERVED)),
	/** 대여 중 */
	ONGOING(EnumSet.of(RentalStatus.RENTING, RentalStatus.OVERDUE, RentalStatus.RETURNED)),
	/** 완료 */
	DONE(EnumSet.of(RentalStatus.COMPLETED, RentalStatus.CANCELED));

	private final Set<RentalStatus> statuses;

	RentalHistoryTab(EnumSet<RentalStatus> statuses) {
		this.statuses = Collections.unmodifiableSet(statuses);
	}
}
