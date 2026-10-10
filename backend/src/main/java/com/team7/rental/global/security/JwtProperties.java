package com.team7.rental.global.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 만료 시간 값은 docs/policies.md 회원·인증(auth.access-token-ttl, auth.refresh-token-ttl) 기준.
 *
 * @param secret HMAC 서명 키 (JWT_SECRET). 32바이트(256bit) 이상
 * @param accessTokenTtl Access Token 만료 시간
 * @param refreshTokenTtl Refresh Token 만료 시간
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
