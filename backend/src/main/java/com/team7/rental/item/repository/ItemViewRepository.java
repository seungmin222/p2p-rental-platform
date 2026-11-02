package com.team7.rental.item.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** 중복 판정과 카운터 증가는 하나의 Lua로 실행한다. 단일 Redis 구성. */
@Repository
@RequiredArgsConstructor
public class ItemViewRepository {
	private static final String DIRTY = "viewcount:dirty";
	private final StringRedisTemplate redis;
	private final com.team7.rental.global.policy.PolicyProperties policies;

	private static final DefaultRedisScript<Long> RECORD = new DefaultRedisScript<>("""
		local current = tonumber(redis.call('GET', KEYS[2]) or ARGV[1])
		current = math.max(current, tonumber(ARGV[1]))
		if redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[2]) then
		  current = math.min(current + 1, 2147483647)
		  redis.call('SADD', KEYS[3], ARGV[3])
		end
		redis.call('SET', KEYS[2], tostring(current))
		return current
		""", Long.class);

	private static final DefaultRedisScript<Long> ACK = new DefaultRedisScript<>("""
		if redis.call('GET', KEYS[1]) == ARGV[2] then
		  return redis.call('SREM', KEYS[2], ARGV[1])
		end
		return 0
		""", Long.class);

	public int record(Long itemId, Long memberId, String remoteAddress, int databaseCount) {
		Long count = redis.execute(RECORD,
			List.of(dedupeKey(itemId, memberId, remoteAddress), "viewcount:" + itemId, DIRTY),
			Integer.toString(databaseCount),
			Long.toString(Duration.ofHours(policies.viewCount().dedupeHours()).toMillis()), itemId.toString());
		if (count == null) {
			throw new IllegalStateException("조회수 집계 응답 없음");
		}
		return Math.toIntExact(count);
	}

	public Cursor<String> pendingItems() {
		return redis.opsForSet().scan(DIRTY, ScanOptions.scanOptions().count(100).build());
	}

	public Integer snapshot(Long itemId) {
		String value = redis.opsForValue().get("viewcount:" + itemId);
		return value == null ? null : Integer.valueOf(value);
	}

	/** DB 커밋 후에만 호출한다. 집계 중 새 조회가 들어왔으면 다음 회차에 다시 반영한다. */
	public void acknowledge(Long itemId, int count) {
		redis.execute(ACK, List.of("viewcount:" + itemId, DIRTY), itemId.toString(), Integer.toString(count));
	}

	public static String dedupeKey(Long itemId, Long memberId, String remoteAddress) {
		if (memberId != null) {
			return "view:" + itemId + ":member:" + memberId;
		}
		if (remoteAddress == null || remoteAddress.isBlank()) {
			throw new IllegalArgumentException("비회원 조회에는 접속 주소가 필요합니다.");
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(remoteAddress.getBytes(StandardCharsets.UTF_8));
			return "view:" + itemId + ":ip:" + HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
