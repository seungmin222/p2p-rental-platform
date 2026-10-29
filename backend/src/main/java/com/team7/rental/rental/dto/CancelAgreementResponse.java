package com.team7.rental.rental.dto;

import java.time.LocalDateTime;

import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.RentalCancelAgreement;

import io.swagger.v3.oas.annotations.media.Schema;

/** 양측 동의 취소 요청 결과 (REQ-015) */
public record CancelAgreementResponse(
	@Schema(description = "동의 취소 요청 ID", example = "1")
	Long agreementId,

	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "요청 상태")
	CancelAgreementStatus status,

	@Schema(description = "조회한 회원이 요청자면 true")
	boolean requestedByMe,

	@Schema(description = "취소 사유")
	CancelReason reason,

	@Schema(description = "취소 사유 텍스트 (사유 OTHER)")
	String reasonText,

	@Schema(description = "상대가 응답하지 않으면 만료되는 시각 (policies.md cancel-agreement.expire-hours)")
	LocalDateTime expiresAt) {

	public static CancelAgreementResponse of(RentalCancelAgreement agreement, Long memberId) {
		return new CancelAgreementResponse(agreement.getId(), agreement.getRentalId(), agreement.getStatus(),
			agreement.isRequestedBy(memberId), agreement.getReason(), agreement.getReasonText(),
			agreement.getExpiresAt());
	}
}
