package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인·재발급 응답 (REQ-002·003, 재동의 여부 REQ-037). Refresh Token은 바디에 없고 HttpOnly 쿠키로만 내려간다 */
public record AuthTokenResponse(
	@Schema(description = "Access Token (JWT). Authorization: Bearer 헤더에 넣는다. 만료는 policies.md auth.access-token-ttl", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
	String accessToken,

	@Schema(description = "약관이 개정돼 필수 항목(이용약관·개인정보 수집·이용·만 14세 확인) 중 현재 버전에 동의하지 않은 것이 있으면 true (REQ-037). "
		+ "true면 재동의 화면으로 보내 POST /api/members/me/terms-agreements를 부른다. 거부하면 이용 제한을 안내한다")
	boolean requiresTermsAgreement) {
}
