package com.team7.rental.payment.dto;

import java.time.LocalDateTime;

import com.team7.rental.rental.dto.RentalDepositInfo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 보류 7일 초과 건 (관리자 확정 대상, REQ-049) */
public record HeldDepositResponse(
	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "물품 ID", example = "1")
	Long itemId,

	@Schema(description = "제공자 회원 ID", example = "2")
	Long ownerId,

	@Schema(description = "대여자 회원 ID", example = "3")
	Long renterId,

	@Schema(description = "보증금 (예약 시점)", example = "100000")
	int deposit,

	@Schema(description = "확정된 연체료", example = "15000")
	int overdueFee,

	@Schema(description = "남은 보증금 = 보증금 − 연체료", example = "85000")
	int remainingDeposit,

	@Schema(description = "미반납 신고 건인지. 신고 건은 D 등급만 확정할 수 있다", example = "false")
	boolean lostReported,

	@Schema(description = "보증금 보류 시작 시각")
	LocalDateTime heldAt,

	@Schema(description = "가장 최근 차감 제안. 없으면 null")
	RentalDepositInfo.Proposal latestProposal) {
}
