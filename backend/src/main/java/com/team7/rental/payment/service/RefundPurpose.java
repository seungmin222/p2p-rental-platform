package com.team7.rental.payment.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 환불 종류. 멱등성 키 형식은 docs/domains/payment.md 그대로다 */
@Getter
@RequiredArgsConstructor
public enum RefundPurpose {

	CANCEL("cancel-refund:", "대여 취소 환불"),
	DEPOSIT("deposit-refund:", "보증금 반환");

	private final String keyPrefix;
	private final String cancelReason;

	public String keyOf(Long rentalId) {
		return keyPrefix + rentalId;
	}
}
