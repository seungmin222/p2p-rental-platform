package com.team7.rental.rental.service;

/**
 * 대여 흐름이 부르는 환불. 구현은 결제 도메인에 있어 rental이 payment를 직접 알지 않는다(순환 의존 방지).
 * prepare*는 호출하는 쪽의 트랜잭션에 참여하고(TX1), {@link #execute}는 트랜잭션 밖에서 PG를 부른다.
 */
public interface RentalRefundPort {

	/** 커밋 후 {@link #execute}에 넘기는 환불 요청 */
	record RefundTicket(Long rentalId, Long paymentId, Long transactionId, String paymentKey, int amount,
		String cancelReason, String idempotencyKey) {
	}

	/** PG를 부르고 결과를 반영한다(TX2). 실패해도 던지지 않고 환불이 남아 재시도 배치가 처리한다 */
	void execute(RefundTicket ticket);
}
