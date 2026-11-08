package com.team7.rental.payment.entity;

/** 정산 종류 (state-machines.md §5, erd settlements.source). 거래 완료 또는 수수료가 있는 취소 */
public enum SettlementSource {
	COMPLETED, CANCEL_FEE
}
