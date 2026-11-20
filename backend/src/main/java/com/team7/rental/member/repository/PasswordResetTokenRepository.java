package com.team7.rental.member.repository;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** 비밀번호 재설정 토큰과 요청 제한 (REQ-036). 키는 docs/erd.md Redis 키 규약 pwreset:* */
@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepository {

	private static final String TOKEN_PREFIX = "pwreset:";
	private static final String RATE_PREFIX = "pwreset:rate:";

	private final StringRedisTemplate redis;

	/**
	 * 같은 이메일의 요청 제한 키를 잡는다(SET NX EX). 가입 여부와 상관없이 잡는다.
	 *
	 * @param normalizedEmail 소문자로 바꾼 이메일
	 * @return 처음 요청이면 true, 제한 시간 안의 재요청이면 false
	 */
	public boolean acquireRateLimit(String normalizedEmail, Duration ttl) {
		return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(RATE_PREFIX + normalizedEmail, "1", ttl));
	}

	public void save(String token, Long memberId, Duration ttl) {
		redis.opsForValue().set(TOKEN_PREFIX + token, memberId.toString(), ttl);
	}

	/** 토큰을 읽으면서 지운다(GETDEL). 1회용이라 두 번째 호출은 비어 있다 */
	public Optional<Long> consume(String token) {
		return Optional.ofNullable(redis.opsForValue().getAndDelete(TOKEN_PREFIX + token)).map(Long::valueOf);
	}
}
