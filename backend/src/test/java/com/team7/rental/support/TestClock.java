package com.team7.rental.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.team7.rental.global.config.TimeConfig;

/**
 * 테스트에서 시각을 바꿀 수 있는 Clock. 고정하지 않으면 실제 시각(Asia/Seoul)을 따른다.
 * 예: {@code testClock.fixAt(LocalDateTime.of(2026, 5, 1, 10, 0))}, {@code testClock.advance(Duration.ofDays(1))}
 */
public class TestClock extends Clock {

	private volatile Clock delegate = Clock.system(TimeConfig.ZONE_ID);

	/** Asia/Seoul 기준 시각으로 고정한다. */
	public void fixAt(LocalDateTime dateTime) {
		delegate = Clock.fixed(dateTime.atZone(TimeConfig.ZONE_ID).toInstant(), TimeConfig.ZONE_ID);
	}

	/** 현재 시각(고정돼 있으면 고정 시각)에서 duration만큼 옮겨 고정한다. */
	public void advance(Duration duration) {
		delegate = Clock.fixed(delegate.instant().plus(duration), TimeConfig.ZONE_ID);
	}

	/** 실제 시각으로 되돌린다. IntegrationTestSupport가 각 테스트 뒤에 호출한다. */
	public void reset() {
		delegate = Clock.system(TimeConfig.ZONE_ID);
	}

	@Override
	public ZoneId getZone() {
		return delegate.getZone();
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return delegate.withZone(zone);
	}

	@Override
	public Instant instant() {
		return delegate.instant();
	}
}
