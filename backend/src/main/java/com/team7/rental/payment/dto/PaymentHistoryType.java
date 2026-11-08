package com.team7.rental.payment.dto;

/**
 * 결제·보증금 내역 필터 (REQ-033). 의미는 확정 전 임시 해석이다(open-issues #44). 모든 값은 READY·FAILED(돈이 오가지 않은 주문)를 빼고 본다.
 * <ul>
 *   <li>ALL: 전체</li>
 *   <li>PAYMENT: 승인된 결제 전체(지금은 ALL과 같다)</li>
 *   <li>REFUND: 취소 환불이 있는 건(대여 CANCELED이고 환불했거나 환불이 남아 있음)</li>
 *   <li>DEPOSIT: 보증금이 동결 이후 단계(HELD·REFUNDED·FORFEITED·REFUND_FAILED)로 넘어간 건</li>
 * </ul>
 */
public enum PaymentHistoryType {
	ALL, PAYMENT, REFUND, DEPOSIT
}
