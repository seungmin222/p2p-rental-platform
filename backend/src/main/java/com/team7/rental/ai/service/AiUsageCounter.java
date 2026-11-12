package com.team7.rental.ai.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;

import lombok.RequiredArgsConstructor;

/**
 * AI 호출 월 사용량 (ai.monthly-limit, Redis {@code ai:usage:{yyyyMM}}, docs/erd.md Redis 키 규약). 호출 직전에 1 올리고 한도 안이면 부른다.
 * 재시도도 1건으로 센다(open-issues #47). 키는 그달 말 + 1일에 만료된다.
 */
@Component
@RequiredArgsConstructor
public class AiUsageCounter {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

	/** 처음 올릴 때만 만료 시각을 건다 */
	private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then
		  redis.call('EXPIREAT', KEYS[1], ARGV[1])
		end
		return count
		""", Long.class);

	private final StringRedisTemplate redis;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 이번 달 호출 한 건을 잡는다. 한도를 넘으면 false(그 건은 부르지 않는다. 카운터는 올라간 채로 둔다).
	 *
	 * @throws org.springframework.dao.DataAccessException Redis 실패 (부르는 쪽이 FAILED로 끝낸다)
	 */
	public boolean tryAcquire() {
		YearMonth month = YearMonth.now(clock);
		// 다음 달 2일 00:00 = 그달 말일이 끝난 뒤 하루 더 = erd.md "월 말 + 1일"
		long expireAt = month.atEndOfMonth().plusDays(2).atStartOfDay(clock.getZone()).toEpochSecond();
		Long count = redis.execute(INCREMENT, List.of(key(month)), Long.toString(expireAt));
		if (count == null) {
			throw new IllegalStateException("AI 사용량 카운터 응답 없음");
		}
		return count <= policyProperties.ai().monthlyLimit();
	}

	/**
	 * 회원의 오늘 카테고리 추천 한 건을 잡는다 (ai.category.per-member-daily, REQ-067). 한도를 넘으면 false. 키는 다음 날이 끝날 때 만료된다.
	 * 추천 요청 단위로 세고 형식 오류 재시도는 세지 않는다(월 한도는 호출마다 {@link #tryAcquire}로 따로 센다).
	 *
	 * @throws org.springframework.dao.DataAccessException Redis 실패 (부르는 쪽이 FAILED로 끝낸다)
	 */
	public boolean tryAcquireCategory(Long memberId) {
		LocalDate today = LocalDate.now(clock);
		long expireAt = today.plusDays(2).atStartOfDay(clock.getZone()).toEpochSecond();
		Long count = redis.execute(INCREMENT, List.of(categoryKey(memberId, today)), Long.toString(expireAt));
		if (count == null) {
			throw new IllegalStateException("AI 카테고리 추천 횟수 카운터 응답 없음");
		}
		return count <= policyProperties.ai().category().perMemberDaily();
	}

	public static String categoryKey(Long memberId, LocalDate day) {
		return "ai:category:" + memberId + ":" + day.format(DAY);
	}

	public static String key(YearMonth month) {
		return "ai:usage:" + month.format(MONTH);
	}
}
