package com.team7.rental.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 이 Clock으로만 구한다(LocalDate.now(clock)). 테스트에서는 고정 Clock으로 교체한다.
 */
@Configuration
public class TimeConfig {

	/** 서버·DB 타임존 (docs/architecture.md §5) */
	public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(ZONE_ID);
	}
}
