package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그인 (REQ-002). 형식·길이는 검사하지 않는다(틀리면 계정 불일치 401로 같게 응답) */
public record AuthLoginRequest(
	@Schema(description = "이메일", example = "user@example.com") @NotBlank
	String email,

	@Schema(description = "비밀번호", example = "rental123!") @NotBlank
	String password) {
}
