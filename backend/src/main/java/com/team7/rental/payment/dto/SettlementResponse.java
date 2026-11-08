package com.team7.rental.payment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.dto.RentalSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/** 정산 한 건 (REQ-039). 대여 필드 이름은 RentalDetailResponse와 같다. 항목을 누르면 rentalId로 대여 상세로 간다 */
public record SettlementResponse(
	@Schema(description = "정산 ID", example = "1")
	Long settlementId,

	@Schema(description = "대여 ID. 대여 상세(GET /api/rentals/{rentalId})로 이동할 때 쓴다", example = "10")
	Long rentalId,

	@Schema(description = "물품")
	RentalItem item,

	@Schema(description = "대여 시작일", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "정산 종류: COMPLETED 거래 완료, CANCEL_FEE 수수료 있는 취소")
	SettlementSource source,

	@Schema(description = "수수료 대상 금액(원): 대여료 + 연장료 + 연체료, 또는 취소 수수료", example = "45000")
	int grossAmount,

	@Schema(description = "플랫폼 수수료(원): floor(grossAmount × policies.md settlement.fee-rate)", example = "4500")
	int feeAmount,

	@Schema(description = "손상 차감액(원). 수수료를 떼지 않고 지급액에 더한다. 취소 정산이면 0", example = "20000")
	int deductionAmount,

	@Schema(description = "지급액(원) = grossAmount − feeAmount + deductionAmount", example = "60500")
	int payoutAmount,

	@Schema(description = "지급 예정일: 완료(취소)일 + 영업일 2일(주말만 제외)", example = "2026-11-10")
	LocalDate scheduledDate,

	@Schema(description = "정산 상태: SCHEDULED 지급 예정, HELD 보류(분쟁), PAID 지급 완료(실제 송금 없음)")
	SettlementStatus status,

	@Schema(description = "지급 완료 시각. PAID가 아니면 null")
	LocalDateTime paidAt) {

	public static SettlementResponse of(Settlement settlement, RentalSummary rental) {
		return new SettlementResponse(settlement.getId(), settlement.getRentalId(), rental.item(),
			rental.startDate(), rental.endDate(), settlement.getSource(), settlement.getGrossAmount(),
			settlement.getFeeAmount(), settlement.getDeductionAmount(), settlement.getPayoutAmount(),
			settlement.getScheduledDate(), settlement.getStatus(), settlement.getPaidAt());
	}
}
