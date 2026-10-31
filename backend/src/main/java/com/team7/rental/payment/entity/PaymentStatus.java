package com.team7.rental.payment.entity;

/** 결제 상태 (docs/state-machines.md §4) */
public enum PaymentStatus {
	READY,
	APPROVED,
	CANCELED,
	PARTIAL_CANCELED,
	FAILED
}
