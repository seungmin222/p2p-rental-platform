package com.team7.rental.rental.service;

import java.util.Optional;

/**
 * 대여 흐름이 부르는 환불. 구현은 결제 도메인에 있어 rental이 payment를 직접 알지 않는다(순환 의존 방지).
 * prepare*는 호출하는 쪽의 트랜잭션(대여 행 락 안)에 참여하고(TX1), {@link #execute}는 트랜잭션 밖에서 PG를 부른다.
 */
public interface RentalRefundPort {

	/**
	 * 커밋 후 {@link #execute}에 넘기는 환불 요청.
	 *
	 * @param completion 환불이 끝나면 대여를 COMPLETED로 바꿀 때의 이력 정보. 취소 환불이면 null
	 * @param retry 재시도 배치의 시도인지. 실패 횟수를 센다
	 */
	record RefundTicket(Long rentalId, Long paymentId, Long transactionId, String paymentKey, int amount,
		String cancelReason, String idempotencyKey, Completion completion, boolean retry) {
	}

	/** @param changedBy 판정한 제공자. 시스템이면 null */
	record Completion(Long changedBy, String reason) {
	}

	/**
	 * 취소 환불을 준비한다. 환불액 = 대여료 − 수수료 + 보증금 (policies.md §2). 결제 대기의 READY 주문은 닫고 PG를 부르지 않는다.
	 *
	 * @return 승인된 결제가 있고 환불액 &gt; 0이면 환불 요청. 그 외(결제 없음·결제 대기·0원)는 비어 있다
	 */
	Optional<RefundTicket> prepareCancelRefund(Long rentalId, int cancelFee);

	/**
	 * 보증금 반환을 준비한다 (REQ-025). 반환액 = 보증금 − 연체료. 0원이면 PG 없이 FORFEITED로 끝낸다.
	 *
	 * @param overdueFee rentals.overdue_fee. REQ-023 전에는 0이다
	 * @param completion 반환이 끝날 때 대여 완료 이력에 남길 정보
	 * @return 비어 있으면 PG가 필요 없어 호출하는 쪽이 바로 COMPLETED로 바꾼다(결제 없음·보증금 0원·반환액 0원). 있으면 대여는 RETURNED로 두고
	 *     {@link #execute}가 끝낸다
	 */
	Optional<RefundTicket> prepareDepositReturn(Long rentalId, int overdueFee, Completion completion);

	/** 동결된 보증금이 있는지. 상태 확인 '이상'이면 REQ-049까지 RETURNED로 둔다 */
	boolean hasFrozenDeposit(Long rentalId);

	/** PG를 부르고 결과를 반영한다(TX2). 실패해도 던지지 않고 환불이 남아 재시도 배치가 처리한다 */
	void execute(RefundTicket ticket);
}
