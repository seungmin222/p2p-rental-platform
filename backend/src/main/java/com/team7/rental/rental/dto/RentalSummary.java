package com.team7.rental.rental.dto;

import java.time.LocalDate;

import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.entity.RentalStatus;

/**
 * 다른 도메인 목록(정산 REQ-039, 결제·보증금 내역 REQ-033)이 대여 상세로 이동할 때 보여 줄 대여 요약. 필드 이름은 RentalDetailResponse와 같다.
 *
 * @param overdueFee rentals.overdue_fee (반납 요청 때 확정)
 * @param cancelFee rentals.cancel_fee
 */
public record RentalSummary(Long rentalId, RentalItem item, LocalDate startDate, LocalDate endDate,
	RentalStatus status, int overdueFee, int cancelFee) {
}
