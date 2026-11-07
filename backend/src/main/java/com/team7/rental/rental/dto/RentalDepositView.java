package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;

/**
 * 보증금 보류·차감(REQ-049, 결제 도메인)이 보는 대여 정보. 엔티티를 넘기지 않는다.
 *
 * @param deposit 예약 시점 보증금 스냅샷
 * @param overdueFee 반납 요청·미반납 신고 때 확정된 연체료
 * @param lostReported 반납 기록이 미반납 신고인지. 신고 건은 D 등급만 제안할 수 있다
 */
public record RentalDepositView(Long rentalId, Long itemId, Long ownerId, Long renterId, RentalStatus status,
	int deposit, int overdueFee, boolean lostReported) {

	public boolean isParticipant(Long memberId) {
		return ownerId.equals(memberId) || renterId.equals(memberId);
	}

	/** 당사자가 아니면 null */
	public RentalRole roleOf(Long memberId) {
		if (ownerId.equals(memberId)) {
			return RentalRole.OWNER;
		}
		return renterId.equals(memberId) ? RentalRole.RENTER : null;
	}

	/** 남은 보증금 = 보증금 − 연체료 (policies.md §4) */
	public int remainingDeposit() {
		return deposit - overdueFee;
	}
}
