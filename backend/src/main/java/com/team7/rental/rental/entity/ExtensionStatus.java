package com.team7.rental.rental.entity;

/** 대여 연장 상태 (docs/state-machines.md §5, REQ-038). PENDING_PAYMENT는 expires_at 전까지 연장 기간을 선점한다 */
public enum ExtensionStatus {
	PENDING_PAYMENT, CONFIRMED, CANCELED
}
