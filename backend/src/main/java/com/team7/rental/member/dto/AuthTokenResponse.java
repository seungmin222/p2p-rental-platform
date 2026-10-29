package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인·재발급 응답 (REQ-002·003). Refresh Token은 바디에 없고 HttpOnly 쿠키로만 내려간다 */
public record AuthTokenResponse(
	@Schema(description = "Access Token (JWT). Authorization: Bearer 헤더에 넣는다. 만료는 policies.md auth.access-token-ttl", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
	String accessToken) {
}
