package com.team7.rental.item.dto;

/**
 * 예약(REQ-013)에 필요한 물품 정보. 가격·보증금은 대여 건에 스냅샷한다.
 */
public record ItemRentalTarget(Long itemId, Long ownerId, int pricePerDay, int deposit) {

	public boolean isOwnedBy(Long memberId) {
		return ownerId.equals(memberId);
	}
}
