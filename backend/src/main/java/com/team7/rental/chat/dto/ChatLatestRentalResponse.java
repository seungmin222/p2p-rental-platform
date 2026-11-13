package com.team7.rental.chat.dto;

import java.time.LocalDate;

import com.team7.rental.rental.dto.RentalBrief;
import com.team7.rental.rental.entity.RentalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅방 상단 바의 최근 대여 건 (SCR-10). 상태 표시와 대여 상세 이동용 */
public record ChatLatestRentalResponse(
	@Schema(description = "대여 ID (대여 상세 이동)", example = "11")
	Long rentalId,

	@Schema(description = "대여 상태 (state-machines.md RentalStatus)")
	RentalStatus status,

	@Schema(description = "대여 시작일", example = "2026-11-20")
	LocalDate startDate,

	@Schema(description = "반납 예정일", example = "2026-11-22")
	LocalDate endDate) {

	public static ChatLatestRentalResponse of(RentalBrief rental) {
		return new ChatLatestRentalResponse(rental.rentalId(), rental.status(), rental.startDate(), rental.endDate());
	}
}
