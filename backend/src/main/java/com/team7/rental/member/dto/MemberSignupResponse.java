package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회원가입 결과 (REQ-001). 가입 후 프론트는 로그인 화면으로 이동한다 */
public record MemberSignupResponse(
	@Schema(description = "가입한 회원 ID", example = "1")
	Long memberId) {
}
