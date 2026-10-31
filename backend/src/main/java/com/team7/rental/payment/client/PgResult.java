package com.team7.rental.payment.client;

import java.util.Map;
import java.util.Set;

/** PG 호출 결과. 응답을 받지 못했거나 처리 여부를 알 수 없으면 Unknown이다 */
public sealed interface PgResult {

	String NOT_FOUND = "NOT_FOUND_PAYMENT";

	/** PG 기록에 남길 필드만. 카드 정보를 담지 않는다 */
	Map<String, Object> toRecord();

	record Success(PgPayment payment) implements PgResult {
		@Override
		public Map<String, Object> toRecord() {
			return Map.of("status", payment.status(), "totalAmount", payment.totalAmount(), "balanceAmount",
				payment.balanceAmount());
		}
	}

	record Failure(String code, String message) implements PgResult {
		@Override
		public Map<String, Object> toRecord() {
			return Map.of("code", code, "message", message);
		}
	}

	record Unknown(String reason) implements PgResult {
		@Override
		public Map<String, Object> toRecord() {
			return Map.of("reason", reason);
		}
	}

	/**
	 * @param status 토스 Payment.status
	 * @param totalAmount 최초 결제 금액
	 * @param balanceAmount 취소 가능한 잔액
	 */
	record PgPayment(String paymentKey, String orderId, String status, int totalAmount, int balanceAmount) {

		private static final Set<String> APPROVED = Set.of("DONE", "PARTIAL_CANCELED", "CANCELED");

		/** 승인된 적이 있는지 (이후 취소됐어도 true) */
		public boolean isApproved() {
			return APPROVED.contains(status);
		}
	}
}
