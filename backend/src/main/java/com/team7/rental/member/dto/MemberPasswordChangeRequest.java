package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 변경 (REQ-005). 새 비밀번호 규칙은 가입과 같다 */
@ValidMemberText
public record MemberPasswordChangeRequest(
	@Schema(description = "현재 비밀번호", example = "rental123!") @NotBlank
	String currentPassword,

	@Schema(description = "새 비밀번호. 글자 수는 policies.md member.password, 영문·숫자·특수문자 모두 포함", example = "newpass456@") @NotBlank @Pattern(regexp = MemberTexts.PASSWORD_PATTERN, message = MemberTexts.PASSWORD_PATTERN_MESSAGE)
	String newPassword) {
}
