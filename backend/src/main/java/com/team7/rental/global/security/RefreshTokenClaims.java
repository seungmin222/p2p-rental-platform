package com.team7.rental.global.security;

/**
 * Refresh Token에서 꺼낸 값. Redis rt:{memberId}:{familyId}에 저장된 jti와 비교하는 데 쓴다(REQ-003).
 */
public record RefreshTokenClaims(Long memberId, String familyId, String jti) {
}
