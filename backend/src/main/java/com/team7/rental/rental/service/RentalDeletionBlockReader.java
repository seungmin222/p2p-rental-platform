package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/**
 * 물품 삭제(REQ-008)를 막는 대여({@link RentalStatus#BLOCKING_ITEM_DELETION})를 읽는다. 물품 락 안 트랜잭션에서 부른다.
 * 트랜잭션은 호출하는 쪽을 따른다.
 */
@Component
@RequiredArgsConstructor
public class RentalDeletionBlockReader {

	private final RentalRepository rentalRepository;

	/** 시작일 순 */
	public List<BlockingRental> findBlocking(Long itemId) {
		return rentalRepository
			.findAllByItemIdAndStatusInOrderByStartDateAscIdAsc(itemId, RentalStatus.BLOCKING_ITEM_DELETION).stream()
			.map(rental -> new BlockingRental(rental.getId(), rental.getStatus(), rental.getStartDate(),
				rental.getEndDate()))
			.toList();
	}

	public record BlockingRental(Long rentalId, RentalStatus status, LocalDate startDate, LocalDate endDate) {

		/** error.details reason (예: 2026-11-03 ~ 2026-11-05 대여 예정 (rentalId 12)) */
		public String describe() {
			return startDate + " ~ " + endDate + " " + status.displayName() + " (rentalId " + rentalId + ")";
		}
	}
}
