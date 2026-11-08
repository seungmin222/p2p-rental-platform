package com.team7.rental.payment.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 환불 종류. 멱등성 키 형식은 docs/domains/payment.md 그대로다 */
@Getter
@RequiredArgsConstructor
public enum RefundPurpose {

	CANCEL("cancel-refund:", "대여 취소 환불", false),
	DEPOSIT("deposit-refund:", "보증금 반환", false),
	/** 승인 시점에 연장을 확정할 수 없어 돌려주는 연장료 (REQ-038). 대여당 연장 결제가 여럿이라 결제 ID로 키를 만든다 */
	EXTENSION_CANCEL("extension-refund:", "대여 연장 취소 환불", true);

	private final String keyPrefix;
	private final String cancelReason;
	private final boolean keyedByPayment;

	/** 멱등성 키. CANCEL·DEPOSIT은 {@code {prefix}{rentalId}}, EXTENSION_CANCEL은 {@code {prefix}{paymentId}} */
	public String keyOf(Long rentalId, Long paymentId) {
		return keyPrefix + (keyedByPayment ? paymentId : rentalId);
	}

	/** 저장된 멱등성 키로 환불 종류를 찾는다 */
	public static RefundPurpose ofKey(String idempotencyKey) {
		for (RefundPurpose purpose : values()) {
			if (idempotencyKey.startsWith(purpose.keyPrefix)) {
				return purpose;
			}
		}
		return CANCEL;
	}
}
