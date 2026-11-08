package com.team7.rental.payment.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 환불 멱등성 키 (docs/domains/payment.md). 재시도 배치는 저장된 키로 환불 종류를 되찾는다 */
class RefundPurposeTest {

	@Test
	@DisplayName("대여 취소·보증금 반환 키는 대여 ID, 연장 취소 키는 결제 ID로 만든다")
	void keyOf() {
		assertThat(RefundPurpose.CANCEL.keyOf(1L, 9L)).isEqualTo("cancel-refund:1");
		assertThat(RefundPurpose.DEPOSIT.keyOf(1L, 9L)).isEqualTo("deposit-refund:1");
		assertThat(RefundPurpose.EXTENSION_CANCEL.keyOf(1L, 9L)).isEqualTo("extension-refund:9");
	}

	@Test
	@DisplayName("저장된 키로 종류를 되찾는다: extension-refund:는 EXTENSION_CANCEL(재시도 때 완료 처리 없음)")
	void ofKey() {
		assertThat(RefundPurpose.ofKey("cancel-refund:1")).isEqualTo(RefundPurpose.CANCEL);
		assertThat(RefundPurpose.ofKey("deposit-refund:1")).isEqualTo(RefundPurpose.DEPOSIT);
		assertThat(RefundPurpose.ofKey("extension-refund:9")).isEqualTo(RefundPurpose.EXTENSION_CANCEL);
	}
}
