package com.team7.rental.payment.dto;

import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DeductionType;
import com.team7.rental.payment.service.DeductionLimit;

import io.swagger.v3.oas.annotations.media.Schema;

/** 등급별 차감 상한 (REQ-049, policies.md §4 계산 규칙) */
public record DeductionPolicyResponse(
	@Schema(description = "손상 등급")
	DamageGrade grade,

	@Schema(description = "차감 방식. FIXED(정액), ACTUAL(실비), FULL(전액)")
	DeductionType deductionType,

	@Schema(description = "보증금 (예약 시점)", example = "100000")
	int deposit,

	@Schema(description = "확정된 연체료. 보증금에서 먼저 뺀다", example = "15000")
	int overdueFee,

	@Schema(description = "남은 보증금 = 보증금 − 연체료", example = "85000")
	int remainingDeposit,

	@Schema(description = "제안할 수 있는 최대 금액 = min(등급별 상한, 남은 보증금)", example = "50000")
	int maxAmount,

	@Schema(description = "견적서·영수증·구매 내역 사진(receiptImages)이 필요한지. ACTUAL이면 true", example = "true")
	boolean receiptRequired) {

	public static DeductionPolicyResponse from(DeductionLimit limit) {
		return new DeductionPolicyResponse(limit.grade(), limit.deductionType(), limit.deposit(), limit.overdueFee(),
			limit.remainingDeposit(), limit.maxAmount(), limit.receiptRequired());
	}
}
