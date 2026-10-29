package com.team7.rental.rental.entity;

/** 양측 동의 취소 요청 상태 (docs/state-machines.md §5, REQ-015) */
public enum CancelAgreementStatus {
	REQUESTED, ACCEPTED, REJECTED, EXPIRED
}
