package com.team7.rental.rental.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대여 상세·관리자 보류 목록에 보여 줄 보증금 정보 (REQ-025·049). 결제 도메인이 채운다(RentalDepositQueryPort).
 * 상태·등급은 결제 도메인 Enum을 rental이 직접 알지 않도록 코드 문자열로 담는다(값은 state-machines.md §2·§5).
 *
 * @param depositStatus DepositStatus 코드. 승인된 대여 결제가 없으면 이 객체 자체가 없다
 */
public record RentalDepositInfo(
	@Schema(description = "보증금 상태 (state-machines.md §2 DepositStatus)", allowableValues = {
		"NONE", "FROZEN", "HELD",
		"REFUNDED", "FORFEITED", "REFUND_FAILED"}, example = "HELD")
	String depositStatus,

	@Schema(description = "누적 취소 금액(원). 취소 환불·보증금 반환을 모두 더한 값", example = "0")
	int refundedAmount,

	@Schema(description = "아직 끝나지 않은 환불이 있는지(반환 처리 중·재시도 대기)", example = "false")
	boolean refundPending,

	@Schema(description = "보증금 보류 시작 시각. 보류된 적이 없으면 null")
	LocalDateTime heldAt,

	@Schema(description = "확정된 차감. 확정 전이면 null")
	Deduction deduction,

	@Schema(description = "가장 최근 차감 제안(상태 무관). 없으면 null")
	Proposal latestProposal) {

	public record Deduction(
		@Schema(description = "손상 등급 (DamageGrade)", allowableValues = {
			"B", "C", "D"}, example = "C")
		String grade,

		@Schema(description = "차감액(원)", example = "30000")
		int amount,

		@Schema(description = "차감 사유", example = "렌즈 흠집 수리비")
		String reason) {
	}

	public record Proposal(
		@Schema(description = "차감 제안 ID. 수락·거절 때 보낸다", example = "1")
		Long proposalId,

		@Schema(description = "제안 상태 (DeductionProposalStatus)", allowableValues = {
			"PROPOSED", "ACCEPTED", "REJECTED",
			"AUTO_ACCEPTED", "ADMIN_DECIDED"}, example = "PROPOSED")
		String status,

		@Schema(description = "손상 등급 (DamageGrade)", allowableValues = {"B", "C", "D"}, example = "C")
		String grade,

		@Schema(description = "차감액(원)", example = "30000")
		int amount,

		@Schema(description = "차감 사유", example = "렌즈 흠집 수리비")
		String reason,

		@Schema(description = "손상 증빙 사진 URL. 미반납 신고 건·관리자 확정은 빈 목록일 수 있다")
		List<String> evidenceImageUrls,

		@Schema(description = "견적서·영수증·구매 내역 사진 URL. 없으면 빈 목록")
		List<String> receiptImageUrls,

		@Schema(description = "제안(관리자 확정) 시각")
		LocalDateTime proposedAt,

		@Schema(description = "대여자 응답 기한(제안 + policies.md deduction.renter-response-hours). 지나면 자동 수락된다")
		LocalDateTime respondDeadline,

		@Schema(description = "응답(수락·거절·자동 수락·관리자 확정) 시각. 응답 전이면 null")
		LocalDateTime respondedAt) {
	}
}
