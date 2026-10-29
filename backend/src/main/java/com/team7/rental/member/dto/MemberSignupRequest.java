package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 회원가입 (REQ-001). 글자 수는 {@link ValidMemberText}. 약관 동의(REQ-037, 2순위)는 그 단계에서 필드를 추가한다.
 */
@ValidMemberText
public record MemberSignupRequest(
	@Schema(description = "이메일. 최대 글자 수는 policies.md member.email.max", example = "user@example.com") @NotBlank @Email
	String email,

	@Schema(description = "비밀번호. 글자 수는 policies.md member.password, 영문·숫자·특수문자 모두 포함", example = "rental123!") @NotBlank @Pattern(regexp = MemberTexts.PASSWORD_PATTERN, message = MemberTexts.PASSWORD_PATTERN_MESSAGE)
	String password,

	@Schema(description = "닉네임. 글자 수는 policies.md member.nickname", example = "동네주민") @NotBlank
	String nickname) {
}
