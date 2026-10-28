package com.team7.rental.rental.entity;

/** 취소 사유 (docs/state-machines.md §5). OTHER면 사유 텍스트 필수(REQ-015). */
public enum CancelReason {
	SCHEDULE_CHANGE, ITEM_PROBLEM, COUNTERPART_REQUEST, OTHER
}
