package com.team7.rental.rental.entity;

/** 취소 종류 (docs/state-machines.md §5). 수수료 계산과 페널티 집계(REQ-062)에 쓴다. */
public enum CancelType {
	RENTER, OWNER, MUTUAL, PAYMENT_EXPIRED, AMOUNT_MISMATCH, NO_HANDOVER
}
