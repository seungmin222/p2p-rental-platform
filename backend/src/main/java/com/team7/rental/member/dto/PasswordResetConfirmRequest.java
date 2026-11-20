package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 재설정 (REQ-036). 새 비밀번호 규칙은 가입과 같다 */
@ValidMemberText
public record PasswordResetConfirmRequest(
	@Schema(description = "재설정 메일 링크의 token 값", example = "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c") @NotBlank
	String token,

	@Schema(description = "새 비밀번호. 글자 수는 policies.md member.password, 공백 없는 ASCII 출력 문자, 영문·숫자·기호 각 1개 이상", example = "newpass456@") @NotBlank @Pattern(regexp = MemberTexts.PASSWORD_PATTERN, message = MemberTexts.PASSWORD_PATTERN_MESSAGE)
	String newPassword) {
}
