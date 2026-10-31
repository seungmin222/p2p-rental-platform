package com.team7.rental.payment.client;

/**
 * PG 호출 (docs/domains/payment.md). 트랜잭션 밖에서 부른다. 결과를 모르는 경우도 예외가 아니라 {@link PgResult.Unknown}으로 돌려준다.
 * 같은 멱등성 키로 다시 부르면 PG가 첫 결과를 돌려주므로 중복 승인·환불이 없다.
 */
public interface PaymentGateway {

	PgResult confirm(String paymentKey, String orderId, int amount, String idempotencyKey);

	/** cancelAmount만큼 취소한다. 전액이어도 금액을 적어 보낸다 */
	PgResult cancel(String paymentKey, int cancelAmount, String cancelReason, String idempotencyKey);

	/** 결제가 없으면 {@link PgResult.Failure}(code {@link PgResult#NOT_FOUND}) */
	PgResult findByOrderId(String orderId);
}
