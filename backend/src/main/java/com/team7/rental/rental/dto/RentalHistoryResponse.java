package com.team7.rental.rental.dto;

import java.time.LocalDate;

import com.team7.rental.rental.dto.RentalDetailResponse.RentalCounterpart;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.entity.RentalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 빌린 내역 한 건 (REQ-019, SCR-12). 물품·상대 정보는 대여 상세와 같은 스키마(RentalItem·RentalCounterpart)를 쓴다.
 */
public record RentalHistoryResponse(
	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "대여 상태")
	RentalStatus status,

	RentalItem item,

	@Schema(description = "제공자")
	RentalCounterpart owner,

	@Schema(description = "대여 시작일 (포함)", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일 (포함)", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "결제 금액 = 대여료 + 보증금 (예약 시점)", example = "80000")
	long totalAmount,

	@Schema(description = "후기 작성 가능 여부. 거래 완료(COMPLETED) 후 policies.md review.write-deadline-days 이내면 true")
	boolean canWriteReview) {
}
