package com.team7.rental.global.security;

import java.time.Instant;

/**
 * 새로 발급한 Refresh Token. jti는 Redis 저장값, expiresAt은 Redis TTL·쿠키 만료 계산에 쓴다.
 */
public record IssuedRefreshToken(String token, String familyId, String jti, Instant expiresAt) {
}
