package com.team7.rental.payment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentHistoryRow;
import com.team7.rental.rental.dto.RentalDepositInfo;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.dto.RentalSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/** 결제·보증금 내역 한 줄 (REQ-033) = 결제 한 건. 대여 필드 이름은 RentalDetailResponse와 같다. 항목을 누르면 rentalId로 대여 상세로 간다 */
public record PaymentHistoryResponse(
	@Schema(description = "결제 ID", example = "1")
	Long paymentId,

	@Schema(description = "대여 ID. 대여 상세(GET /api/rentals/{rentalId})로 이동할 때 쓴다", example = "10")
	Long rentalId,

	@Schema(description = "물품")
	RentalItem item,

	@Schema(description = "대여 시작일", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "결제 종류: RENTAL 대여료+보증금, EXTENSION 연장료(REQ-038)")
	PaymentType paymentType,

	@Schema(description = "결제 시각(주문 생성 시각). from·to 필터도 이 값에 건다")
	LocalDateTime createdAt,

	@Schema(description = "결제 금액(원) = rentalFee + deposit + extensionFee", example = "80000")
	int amount,

	@Schema(description = "대여료(원). 연장 결제면 0", example = "30000")
	int rentalFee,

	@Schema(description = "보증금(원). 연장 결제면 0", example = "50000")
	int deposit,

	@Schema(description = "연장료(원). 대여 결제면 0", example = "0")
	int extensionFee,

	@Schema(description = "결제 상태 (state-machines.md §4)")
	PaymentStatus paymentStatus,

	@Schema(description = "보증금 상태 (state-machines.md §2). 연장 결제면 null")
	DepositStatus depositStatus,

	@Schema(description = "누적 취소 금액(원). 취소 환불·보증금 반환을 모두 더한 값", example = "35000")
	int refundedAmount,

	@Schema(description = "아직 끝나지 않은 환불이 있는지(반환 처리 중·재시도 대기)", example = "false")
	boolean depositRefundPending,

	@Schema(description = "확정 연체료(원). 보증금에서 차감했다", example = "15000")
	int overdueFee,

	@Schema(description = "취소 수수료(원)", example = "0")
	int cancelFee,

	@Schema(description = "확정된 보증금 차감. 차감이 없으면 null")
	RentalDepositInfo.Deduction deduction) {

	public static PaymentHistoryResponse of(PaymentHistoryRow row, RentalSummary rental) {
		boolean extension = row.type() == PaymentType.EXTENSION;
		return new PaymentHistoryResponse(row.paymentId(), row.rentalId(), rental.item(), rental.startDate(),
			rental.endDate(), row.type(), row.createdAt(), row.amount(), extension ? 0 : row.rentalFeeAmount(),
			extension ? 0 : row.depositAmount(), extension ? row.amount() : 0, row.status(), row.depositStatus(),
			row.refundedAmount(), row.refundPendingAmount() != null, rental.overdueFee(), rental.cancelFee(),
			row.damageGrade() == null ? null
				: new RentalDepositInfo.Deduction(row.damageGrade().name(), row.deductionAmount(),
					row.deductionReason()));
	}
}
