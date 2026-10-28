package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.CancelReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 단독 취소·양측 동의 취소 요청 공용 (REQ-015). 사유 텍스트 규칙은 {@link ValidCancelReason} */
@ValidCancelReason
public record RentalCancelRequest(
	@Schema(description = "취소 사유") @NotNull
	CancelReason reason,

	@Schema(description = "사유 텍스트. reason=OTHER면 필수, 최대 글자 수는 policies.md cancel.reason-text-max", example = "개인 사정으로 일정이 바뀌었어요")
	String reasonText) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedReasonText() {
		return reasonText == null || reasonText.isBlank() ? null : reasonText.strip();
	}
}
