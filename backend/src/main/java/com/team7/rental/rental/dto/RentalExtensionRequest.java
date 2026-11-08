package com.team7.rental.rental.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 대여 연장 요청 (REQ-038) */
public record RentalExtensionRequest(
	@Schema(description = "연장 후 종료일. 기존 종료일보다 뒤이고 extension.max-days-per 이내", example = "2026-11-12") @NotNull
	LocalDate newEndDate) {
}
