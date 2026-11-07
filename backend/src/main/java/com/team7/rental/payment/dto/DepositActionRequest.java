package com.team7.rental.payment.dto;

import com.team7.rental.payment.entity.DamageGrade;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 보증금 차감 협의 multipart의 request part (REQ-049). 동작별 필수 항목·사유 길이는 {@link DepositActionRequestValidator}.
 * <ul>
 *   <li>PROPOSE: grade, amount, reason 필수. proposalId는 보내도 무시한다(새 제안이라 가리킬 제안이 없다)</li>
 *   <li>ACCEPT·REJECT: proposalId 필수(대여 상세 latestDeductionProposal.proposalId). 다른 제안에 잘못 응답하지 않게 한다.
 *   grade·amount·reason은 쓰지 않는다(amount의 0 이상 검사는 동작과 상관없이 걸린다)</li>
 * </ul>
 */
@ValidDepositAction
public record DepositActionRequest(
	@Schema(description = "동작. PROPOSE(제공자 제안), ACCEPT·REJECT(대여자 응답)") @NotNull
	DepositAction action,

	@Schema(description = "응답할 차감 제안 ID (ACCEPT·REJECT 필수)", example = "1")
	Long proposalId,

	@Schema(description = "손상 등급 (PROPOSE 필수). A는 차감할 수 없고, 미반납 신고 건은 D만")
	DamageGrade grade,

	@Schema(description = "차감액(원, PROPOSE 필수). 0 이상, 상한은 GET /api/rentals/{id}/deposit/policy의 maxAmount", example = "30000") @PositiveOrZero
	Integer amount,

	@Schema(description = "차감 사유 (PROPOSE 필수). 최대 글자 수는 policies.md deduction.reason.max", example = "렌즈 흠집 수리비")
	String reason) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedReason() {
		return reason == null || reason.isBlank() ? null : reason.strip();
	}
}
