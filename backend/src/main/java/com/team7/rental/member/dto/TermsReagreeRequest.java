package com.team7.rental.member.dto;

import java.util.Set;

import com.team7.rental.member.entity.TermsAgreementType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** 약관 재동의 (REQ-037). 보낸 항목을 현재 버전으로 동의한다. 이미 현재 버전에 동의한 항목은 그대로 둔다 */
public record TermsReagreeRequest(
	@Schema(description = "동의할 항목. 로그인 응답 requiresTermsAgreement가 true면 셋 다 보낸다") @NotEmpty
	Set<@NotNull TermsAgreementType> types) {
}
