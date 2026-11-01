package com.team7.rental.global.security;

/** 검증한 Access Token 정보. familyId는 현재 기기의 토큰 묶음이다. */
public record AccessTokenClaims(Long memberId, String role, String familyId) {
	/** 기존 내부 호출과 테스트의 호환성을 유지한다. 실제 로그인은 familyId를 발급한다. */
	public AccessTokenClaims(Long memberId, String role) {
		this(memberId, role, null);
	}
}
