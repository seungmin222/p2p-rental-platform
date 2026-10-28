package com.team7.rental.rental.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 예약 요청 (REQ-013). 기간 규칙(오늘 이후·1~30일·가능 기간 안)은 서비스가 검사한다 */
public record RentalCreateRequest(
	@Schema(description = "물품 ID", example = "1") @NotNull
	Long itemId,

	@Schema(description = "대여 시작일 (포함)", example = "2026-11-03") @NotNull
	LocalDate startDate,

	@Schema(description = "대여 종료일 (포함). 시작일과 같으면 1일", example = "2026-11-05") @NotNull
	LocalDate endDate) {
}
