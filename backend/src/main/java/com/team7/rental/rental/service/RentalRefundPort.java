package com.team7.rental.rental.service;

import java.util.Optional;

/**
 * 대여 흐름이 부르는 환불. 구현은 결제 도메인에 있어 rental이 payment를 직접 알지 않는다(순환 의존 방지).
 * prepare*는 호출하는 쪽의 트랜잭션에 참여하고(TX1), {@link #execute}는 트랜잭션 밖에서 PG를 부른다.
 */
public interface RentalRefundPort {

	/** 커밋 후 {@link #execute}에 넘기는 환불 요청 */
	record RefundTicket(Long rentalId, Long paymentId, Long transactionId, String paymentKey, int amount,
		String cancelReason, String idempotencyKey) {
	}

	/**
	 * 취소 환불을 준비한다(TX1, 대여 행 락 안). 환불액 = 대여료 − 수수료 + 보증금 (policies.md §2). 결제 대기의 READY 주문은 닫고 PG를 부르지 않는다.
	 *
	 * @return 승인된 결제가 있고 환불액 &gt; 0이면 환불 요청. 그 외(결제 없음·결제 대기·0원)는 비어 있다
	 */
	Optional<RefundTicket> prepareCancelRefund(Long rentalId, int cancelFee);

	/** PG를 부르고 결과를 반영한다(TX2). 실패해도 던지지 않고 환불이 남아 재시도 배치가 처리한다 */
	void execute(RefundTicket ticket);
}
