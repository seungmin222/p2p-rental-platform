package com.team7.rental.rental.dto;

import java.time.LocalDate;

import com.team7.rental.rental.entity.RentalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 물품별 예약 현황 한 건 (REQ-020, 제공자용). 누르면 대여 상세(제공자 뷰)로 간다 */
public record ItemRentalResponse(
	@Schema(description = "대여 ID", example = "32")
	Long rentalId,

	@Schema(description = "대여 상태")
	RentalStatus status,

	@Schema(description = "대여 시작일 (포함)", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일 (포함)", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "대여자")
	Renter renter) {

	public record Renter(
		@Schema(description = "대여자 회원 ID", example = "3")
		Long memberId,

		@Schema(description = "닉네임", example = "텐트초보")
		String nickname) {
	}
}
