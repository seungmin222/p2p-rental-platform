package com.team7.rental.rental.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대여 상세 (REQ-016, SCR-08). 스텝바와 상태 × 역할별 버튼을 그릴 만큼만 담는다.
 * 인계·반납 기록은 GET /api/rentals/{id}/inspections로 따로 본다. 보증금 상태·누적 환불액·차감은 승인된 결제가 있을 때 채운다(REQ-025·049).
 */
public record RentalDetailResponse(
	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "대여 상태")
	RentalStatus status,

	@Schema(description = "조회한 회원의 역할")
	RentalRole myRole,

	RentalItem item,

	@Schema(description = "상대 당사자 (대여자면 제공자, 제공자면 대여자)")
	RentalCounterpart counterpart,

	@Schema(description = "대여 시작일 (포함)", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일 (포함)", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "대여 일수 (양 끝 포함)", example = "3")
	int rentalDays,

	@Schema(description = "일 대여료 (예약 시점)", example = "10000")
	int pricePerDay,

	@Schema(description = "대여료 = 일 대여료 × 일수", example = "30000")
	int rentalFee,

	@Schema(description = "보증금 (예약 시점)", example = "50000")
	int deposit,

	@Schema(description = "결제 금액 = 대여료 + 보증금", example = "80000")
	long totalAmount,

	@Schema(description = "결제 만료 시각. 결제 대기(PENDING_PAYMENT)일 때만 값이 있다")
	LocalDateTime paymentExpiresAt,

	@Schema(description = "인계 확인 시각")
	LocalDateTime handedOverAt,

	@Schema(description = "반납 요청(또는 미반납 신고) 시각")
	LocalDateTime returnedAt,

	@Schema(description = "거래 완료 시각")
	LocalDateTime completedAt,

	@Schema(description = "취소 시각")
	LocalDateTime canceledAt,

	@Schema(description = "취소 종류")
	CancelType cancelType,

	@Schema(description = "취소 사유")
	CancelReason cancelReason,

	@Schema(description = "취소 사유 텍스트 (사유 OTHER)")
	String cancelReasonText,

	@Schema(description = "취소 수수료", example = "0")
	int cancelFee,

	@Schema(description = "연체료 (REQ-023). 연체(OVERDUE)이거나 대여 중(RENTING)인데 반납 예정일이 지났으면 오늘 기준 현재까지 연체료(미확정)다. "
		+ "그 외 상태는 반납 요청 때 확정된 값이고, 반납 전이면 0이다. 계산: min(연체 일수 × floor(일 대여료 × policies.md overdue-fee.rate), 보증금), "
		+ "보증금 0원이면 0", example = "15000")
	int overdueFee,

	@Schema(description = "예약 시각")
	LocalDateTime createdAt,

	@Schema(description = "진행 중인 양측 동의 취소 요청(REQUESTED이고 만료 전). 대여 예정(RESERVED)일 때만 채우고, 그 외 상태나 요청이 없으면 null")
	ActiveCancelAgreement activeCancelAgreement,

	@Schema(description = "보증금 상태 (state-machines.md §2 DepositStatus). 승인된 결제가 없으면(결제 없음·결제 대기·무료 대여) null", allowableValues = {
		"NONE", "FROZEN", "HELD", "REFUNDED", "FORFEITED", "REFUND_FAILED"}, example = "FROZEN")
	String depositStatus,

	@Schema(description = "누적 환불액(원). 취소 환불·보증금 반환을 더한 값, 승인된 결제가 없으면 0", example = "0")
	int refundedAmount,

	@Schema(description = "아직 끝나지 않은 환불이 있는지(보증금 반환 처리 중·재시도 대기). 승인된 결제가 없으면 false", example = "false")
	boolean depositRefundPending,

	@Schema(description = "보증금 보류(HELD) 시작 시각. 보류된 적이 없으면 null. + policies.md deposit.hold-agreement-days가 지나면 관리자 확정 대상")
	LocalDateTime depositHeldAt,

	@Schema(description = "확정된 보증금 차감 (REQ-049). 확정 전이면 null")
	RentalDepositInfo.Deduction deduction,

	@Schema(description = "가장 최근 차감 제안(상태 무관, REQ-049). 대여자는 status PROPOSED이고 respondDeadline 전이면 proposalId로 수락·거절한다. 없으면 null")
	RentalDepositInfo.Proposal latestDeductionProposal,

	@Schema(description = "조회한 회원이 지금 후기를 쓸 수 있는지 (REQ-034). 거래 완료(COMPLETED) 후 policies.md review.write-deadline-days 이내(기한 정각 포함)이고 아직 쓰지 않았으면 true")
	boolean canWriteReview,

	@Schema(description = "조회한 회원이 이 대여에 후기를 이미 썼는지 (REQ-034)")
	boolean reviewWritten) {

	/**
	 * @param activeCancelAgreement 진행 중인 동의 취소 요청. 없으면 null
	 * @param overdueFee 현재까지 또는 확정 연체료 (OverdueFeeCalculator#displayFee)
	 * @param depositInfo 승인된 결제의 보증금 정보. 없으면 null
	 * @param canWriteReview 후기 작성 기간이고 아직 쓰지 않았으면 true
	 * @param reviewWritten 조회한 회원이 이 대여에 후기를 썼으면 true
	 */
	public static RentalDetailResponse of(Rental rental, RentalRole myRole, ItemSummary item, Long counterpartId,
		String counterpartNickname, RentalCancelAgreement activeCancelAgreement, Long memberId, int overdueFee,
		RentalDepositInfo depositInfo, boolean canWriteReview, boolean reviewWritten) {
		return new RentalDetailResponse(
			rental.getId(),
			rental.getStatus(),
			myRole,
			new RentalItem(item.itemId(), item.name(), item.thumbnailUrl(), item.isDeleted()),
			new RentalCounterpart(counterpartId, counterpartNickname),
			rental.getStartDate(),
			rental.getEndDate(),
			Rental.countDays(rental.getStartDate(), rental.getEndDate()),
			rental.getPricePerDay(),
			rental.getRentalFee(),
			rental.getDeposit(),
			(long)rental.getRentalFee() + rental.getDeposit(),
			rental.getPaymentExpiresAt(),
			rental.getHandedOverAt(),
			rental.getReturnedAt(),
			rental.getCompletedAt(),
			rental.getCanceledAt(),
			rental.getCancelType(),
			rental.getCancelReason(),
			rental.getCancelReasonText(),
			rental.getCancelFee(),
			overdueFee,
			rental.getCreatedAt(),
			activeCancelAgreement == null ? null : ActiveCancelAgreement.of(activeCancelAgreement, memberId),
			depositInfo == null ? null : depositInfo.depositStatus(),
			depositInfo == null ? 0 : depositInfo.refundedAmount(),
			depositInfo != null && depositInfo.refundPending(),
			depositInfo == null ? null : depositInfo.heldAt(),
			depositInfo == null ? null : depositInfo.deduction(),
			depositInfo == null ? null : depositInfo.latestProposal(),
			canWriteReview,
			reviewWritten);
	}

	public record RentalItem(
		@Schema(description = "물품 ID", example = "1")
		Long itemId,

		@Schema(description = "물품 이름", example = "4인용 거실형 텐트")
		String name,

		@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없으면 null")
		String thumbnailUrl,

		@Schema(description = "삭제된 물품이면 true (화면에 \"삭제된 물품\" 표시)")
		boolean isDeleted) {
	}

	public record ActiveCancelAgreement(
		@Schema(description = "동의 취소 요청 ID (응답 API 경로에 쓴다)", example = "1")
		Long agreementId,

		@Schema(description = "조회한 회원이 요청자면 true (요청자는 응답할 수 없다)")
		boolean requestedByMe,

		@Schema(description = "취소 사유")
		CancelReason reason,

		@Schema(description = "취소 사유 텍스트 (사유 OTHER)")
		String reasonText,

		@Schema(description = "상대가 응답하지 않으면 만료되는 시각 (policies.md cancel-agreement.expire-hours)")
		LocalDateTime expiresAt) {

		static ActiveCancelAgreement of(RentalCancelAgreement agreement, Long memberId) {
			return new ActiveCancelAgreement(agreement.getId(), agreement.isRequestedBy(memberId),
				agreement.getReason(), agreement.getReasonText(), agreement.getExpiresAt());
		}
	}

	public record RentalCounterpart(
		@Schema(description = "회원 ID", example = "2")
		Long memberId,

		@Schema(description = "닉네임", example = "텐트초보")
		String nickname) {
	}
}
