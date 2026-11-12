package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.RentalStatus;

/** 다른 도메인이 대여 당사자·상태를 확인할 때 쓰는 요약 (문의·분쟁 REQ-063) */
public record RentalParticipants(Long rentalId, Long renterId, Long ownerId, RentalStatus status) {

	public boolean isParticipant(Long memberId) {
		return renterId.equals(memberId) || ownerId.equals(memberId);
	}
}
