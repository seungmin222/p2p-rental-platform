package com.team7.rental.member.repository;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** Redis의 현재 jti와 회원별 family 목록. 비교·교체·재사용 폐기를 한 번에 실행한다. */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {
	private final StringRedisTemplate redis;

	private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
		redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
		redis.call('SADD', KEYS[2], ARGV[3])
		local ttl = redis.call('PTTL', KEYS[2])
		if ttl < tonumber(ARGV[2]) then redis.call('PEXPIRE', KEYS[2], ARGV[2]) end
		return 1
		""", Long.class);

	private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
		local current = redis.call('GET', KEYS[1])
		if not current then
		  redis.call('SREM', KEYS[2], ARGV[4])
		  return 0
		end
		if current ~= ARGV[1] then
		  redis.call('DEL', KEYS[1])
		  redis.call('SREM', KEYS[2], ARGV[4])
		  return 0
		end
		redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
		redis.call('SADD', KEYS[2], ARGV[4])
		local ttl = redis.call('PTTL', KEYS[2])
		if ttl < tonumber(ARGV[3]) then redis.call('PEXPIRE', KEYS[2], ARGV[3]) end
		return 1
		""", Long.class);

	private static final DefaultRedisScript<Long> DELETE = new DefaultRedisScript<>("""
		redis.call('DEL', KEYS[1])
		redis.call('SREM', KEYS[2], ARGV[1])
		return 1
		""", Long.class);

	public void save(Long memberId, String familyId, String jti, Duration ttl) {
		redis.execute(SAVE, keys(memberId, familyId), jti, Long.toString(ttl.toMillis()), familyId);
	}

	public boolean rotate(Long memberId, String familyId, String oldJti, String newJti, Duration ttl) {
		return Long.valueOf(1).equals(redis.execute(ROTATE, keys(memberId, familyId),
			oldJti, newJti, Long.toString(ttl.toMillis()), familyId));
	}

	public void delete(Long memberId, String familyId) {
		redis.execute(DELETE, keys(memberId, familyId), familyId);
	}

	private static final DefaultRedisScript<Long> DELETE_OTHERS = new DefaultRedisScript<>("""
		local families = redis.call('SMEMBERS', KEYS[1])
		local count = 0
		for _, family in ipairs(families) do
		  if family ~= ARGV[1] then
		    redis.call('DEL', ARGV[2] .. family)
		    redis.call('SREM', KEYS[1], family)
		    count = count + 1
		  end
		end
		return count
		""", Long.class);

	/** 단일 Redis 구성. 현재 기기는 유지하고 다른 family만 폐기한다. */
	public void deleteOtherFamilies(Long memberId, String currentFamilyId) {
		redis.execute(DELETE_OTHERS, List.of("rt-families:" + memberId),
			currentFamilyId, "rt:" + memberId + ":");
	}

	private List<String> keys(Long memberId, String familyId) {
		return List.of("rt:" + memberId + ":" + familyId, "rt-families:" + memberId);
	}
}
