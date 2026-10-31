package com.team7.rental.payment.entity;

/** 보증금 상태 (docs/state-machines.md §2). HELD로 가는 전이는 REQ-049에서 추가한다 */
public enum DepositStatus {
	NONE,
	FROZEN,
	HELD,
	REFUNDED,
	FORFEITED,
	REFUND_FAILED
}
