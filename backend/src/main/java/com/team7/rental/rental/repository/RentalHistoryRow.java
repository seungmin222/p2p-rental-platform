package com.team7.rental.rental.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.rental.entity.RentalStatus;

/** 빌린 내역(REQ-019) 한 행에 필요한 rentals 컬럼만 담은 조회 결과 */
public record RentalHistoryRow(
	Long rentalId,
	RentalStatus status,
	Long itemId,
	Long ownerId,
	LocalDate startDate,
	LocalDate endDate,
	int rentalFee,
	int deposit,
	LocalDateTime completedAt) {
}
