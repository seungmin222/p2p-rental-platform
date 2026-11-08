package com.team7.rental.payment.entity;

/** 정산 상태 (state-machines.md §3). HELD는 문의(REQ-063) 분쟁 접수 때만 쓴다(open-issues #9). 그 전이는 문의를 만들 때 추가한다 */
public enum SettlementStatus {
	SCHEDULED, HELD, PAID
}
