package com.team7.rental.rental.dto;

import java.time.LocalDateTime;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

/** 후기 작성 가드(REQ-034)에 필요한 대여 정보. 후기 도메인이 RentalRepository를 직접 부르지 않게 한다 */
public record RentalReviewTarget(Long rentalId, Long renterId, Long ownerId, RentalStatus status,
	LocalDateTime completedAt) {

	public boolean isParticipant(Long memberId) {
		return renterId.equals(memberId) || ownerId.equals(memberId);
	}

	/** 대여자면 제공자, 제공자면 대여자. 당사자인지는 먼저 {@link #isParticipant}로 확인한다 */
	public Long counterpartOf(Long memberId) {
		return renterId.equals(memberId) ? ownerId : renterId;
	}

	public boolean isCompleted() {
		return status == RentalStatus.COMPLETED;
	}

	/** {@link Rental#canWriteReview(RentalStatus, LocalDateTime, LocalDateTime, int)}와 같은 판정(기한 정각 포함) */
	public boolean isWithinReviewDeadline(LocalDateTime now, int deadlineDays) {
		return Rental.canWriteReview(status, completedAt, now, deadlineDays);
	}
}
