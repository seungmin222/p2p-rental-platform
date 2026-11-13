package com.team7.rental.rental.dto;

import java.time.LocalDate;

import com.team7.rental.rental.entity.RentalStatus;

/** 채팅방 상단 바(SCR-10)에 보여 줄 대여 건 요약: 상태 표시와 대여 상세 이동용 */
public record RentalBrief(Long rentalId, RentalStatus status, LocalDate startDate, LocalDate endDate) {
}
