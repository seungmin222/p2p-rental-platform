package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 재설정 (REQ-036). 새 비밀번호 규칙은 가입과 같다. 토큰 형식(Base64URL 43자)은 Bean Validation이 아니라 {@link #hasWellFormedToken()}으로
 * 확인해, 형식이 틀린 토큰도 없는 토큰과 같은 400 AUTH_RESET_TOKEN_INVALID로 답한다
 */
@ValidMemberText
public record PasswordResetConfirmRequest(
	@Schema(description = "재설정 메일 링크의 token 값(Base64URL 43자)", pattern = PasswordResetConfirmRequest.TOKEN_PATTERN, example = "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c") @NotBlank
	String token,

	@Schema(description = "새 비밀번호. 글자 수는 policies.md member.password, 공백 없는 ASCII 출력 문자, 영문·숫자·기호 각 1개 이상", example = "newpass456@") @NotBlank @Pattern(regexp = MemberTexts.PASSWORD_PATTERN, message = MemberTexts.PASSWORD_PATTERN_MESSAGE)
	String newPassword) {

	/** 발급 토큰 형식: 32바이트 Base64URL(패딩 없음) */
	public static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

	/** Redis 키에 붙이기 전에 형식을 확인한다. 다른 pwreset:* 키(요청 제한 등)를 토큰으로 읽지 못하게 한다 */
	public boolean hasWellFormedToken() {
		return token != null && token.matches(TOKEN_PATTERN);
	}
}
