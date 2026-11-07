package com.team7.rental.payment.dto;

import com.team7.rental.payment.entity.DamageGrade;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/** 관리자 보증금 차감 확정 (REQ-049, 057에서 앞당김). 필수 항목·사유 길이는 {@link AdminDepositDecisionRequestValidator} */
@ValidDepositAction
public record AdminDepositDecisionRequest(
	@Schema(description = "손상 등급 (필수). A는 차감할 수 없고, 미반납 신고 건은 D만")
	DamageGrade grade,

	@Schema(description = "차감액(원, 필수). 0 이상, 상한은 제공자 제안과 같다(policies.md §4)", example = "30000") @PositiveOrZero
	Integer amount,

	@Schema(description = "확정 사유 (필수). 최대 글자 수는 policies.md deduction.reason.max", example = "증빙 검토 결과 C 등급 수리비 인정")
	String reason) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedReason() {
		return reason == null || reason.isBlank() ? null : reason.strip();
	}
}
