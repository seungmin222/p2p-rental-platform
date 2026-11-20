package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 가입 시 필수 동의 (REQ-037). 셋 다 true여야 한다. false거나 생략하면 400(details agreements.{필드}). 동의하면 각 항목의 현재 버전으로
 * 기록한다.
 */
public record TermsAgreementsRequest(
	@Schema(description = "이용약관 동의 (필수, true)", example = "true") @NotNull(message = "이용약관에 동의해 주세요.") @AssertTrue(message = "이용약관에 동의해 주세요.")
	Boolean termsOfService,

	@Schema(description = "개인정보 수집·이용 동의 (필수, true)", example = "true") @NotNull(message = "개인정보 수집·이용에 동의해 주세요.") @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
	Boolean privacyCollection,

	@Schema(description = "만 14세 이상 확인 (필수, true)", example = "true") @NotNull(message = "만 14세 이상만 가입할 수 있습니다.") @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
	Boolean ageOver14) {
}
