package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 메일 요청 (REQ-036). 가입 여부와 상관없이 응답은 같다 */
public record PasswordResetRequest(
	@Schema(description = "가입한 이메일", example = "user@example.com") @NotBlank @Email
	String email) {
}
