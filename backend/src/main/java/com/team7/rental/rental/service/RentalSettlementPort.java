package com.team7.rental.rental.service;

import java.time.LocalDateTime;

/**
 * 대여 흐름이 부르는 제공자 정산 생성 (REQ-039). 구현은 결제 도메인에 있어 rental이 payment를 직접 알지 않는다(순환 의존 방지).
 * 호출하는 쪽의 트랜잭션(대여 행 락 안)에 참여한다: 대여 완료·취소와 정산이 함께 커밋되거나 함께 롤백돼, 커밋 뒤 이벤트가 유실돼 정산이 빠지는 일이 없다.
 * 같은 대여에 두 번 불려도 정산은 하나다.
 */
public interface RentalSettlementPort {

	/**
	 * 거래 완료(RETURNED → COMPLETED) 정산. 받은 돈이 없으면(승인된 결제 없음: 결제 기능을 끄고 만든 건·무료 대여) 또는 지급액이 0원이면 만들지 않는다.
	 *
	 * @param overdueFee rentals.overdue_fee (보증금 한도로 확정된 연체료)
	 * @param completedAt rentals.completed_at. 지급 예정일 기준
	 */
	void createForCompletion(Long rentalId, Long ownerId, int overdueFee, LocalDateTime completedAt);

	/**
	 * 수수료가 있는 취소 정산. cancelFee가 0이거나 승인된 결제가 없으면 만들지 않는다. 대여자 환불 결과(성공·실패)와 상관없이 수수료는 제공자 몫이다.
	 *
	 * @param canceledAt rentals.canceled_at. 지급 예정일 기준
	 */
	void createForCancelFee(Long rentalId, Long ownerId, int cancelFee, LocalDateTime canceledAt);
}
