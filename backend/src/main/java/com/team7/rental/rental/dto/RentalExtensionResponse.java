package com.team7.rental.rental.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.rental.entity.ExtensionStatus;
import com.team7.rental.rental.entity.RentalExtension;

import io.swagger.v3.oas.annotations.media.Schema;

public record RentalExtensionResponse(
	@Schema(description = "연장 ID. 결제가 필요하면 POST /api/payments에 rentalId와 함께 보낸다", example = "3")
	Long extensionId,

	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "PENDING_PAYMENT면 결제 대기(expiresAt까지 기간 선점), CONFIRMED면 결제 없이 바로 확정(연장료 0원이거나, 결제 기능이 꺼진 경우에도 CONFIRMED)")
	ExtensionStatus status,

	@Schema(description = "연장 전 종료일", example = "2026-11-08")
	LocalDate prevEndDate,

	@Schema(description = "연장 후 종료일", example = "2026-11-12")
	LocalDate newEndDate,

	@Schema(description = "연장료(원) = 예약 시점 1일 대여료 × 연장 일수. 보증금은 그대로", example = "20000")
	int additionalFee,

	@Schema(description = "결제 대기 만료 시각. 결제 없이 확정했으면 null", example = "2026-11-08T21:15:00")
	LocalDateTime expiresAt) {

	public static RentalExtensionResponse of(RentalExtension extension) {
		boolean pending = extension.getStatus() == ExtensionStatus.PENDING_PAYMENT;
		return new RentalExtensionResponse(extension.getId(), extension.getRentalId(), extension.getStatus(),
			extension.getPrevEndDate(), extension.getNewEndDate(), extension.getAdditionalFee(),
			pending ? extension.getExpiresAt() : null);
	}
}
