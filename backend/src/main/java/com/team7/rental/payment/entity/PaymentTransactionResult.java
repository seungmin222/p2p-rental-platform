package com.team7.rental.payment.entity;

/** PG 호출 결과 (docs/erd.md payment_transactions.result). PENDING은 호출 전 기록이고, 결과를 모르면 그대로 남는다 */
public enum PaymentTransactionResult {
	PENDING,
	SUCCESS,
	FAIL
}
