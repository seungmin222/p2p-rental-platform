package com.team7.rental.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * 인스턴스가 여러 개여도 같은 회차의 @Scheduled 작업이 한 번만 실행되게 한다(docs/architecture.md §2).
 * 모든 @Scheduled 메서드에 @SchedulerLock(name = "도메인.배치명")을 붙인다. 규칙은 backend/CLAUDE.md "배치 단일 실행".
 *
 * <p>defaultLockAtMostFor는 @SchedulerLock에 lockAtMostFor를 빠뜨렸을 때의 안전장치다.
 * 서버가 락을 쥔 채 죽어도 이 시간이 지나면 다른 서버가 실행할 수 있다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulingConfig {

	/**
	 * 락 키: job-lock:{환경}:{락 이름}. 환경(local·test·prod)을 넣어 같은 Redis를 공유해도 서로의 락을 잡지 않게 한다.
	 */
	@Bean
	public LockProvider lockProvider(RedisConnectionFactory connectionFactory,
		@Value("${app.shedlock.environment}")
		String environment) {
		return new RedisLockProvider.Builder(connectionFactory)
			.environment(environment)
			.build();
	}
}
