package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 약관 재동의 결과 (REQ-037) */
public record TermsAgreementStatusResponse(
	@Schema(description = "아직 현재 버전에 동의하지 않은 필수 항목이 있으면 true (로그인 응답과 같은 기준)")
	boolean requiresTermsAgreement) {
}
