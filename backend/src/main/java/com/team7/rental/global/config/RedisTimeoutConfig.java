package com.team7.rental.global.config;

import java.time.Duration;

import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisTimeoutConfig.Settings.class)
public class RedisTimeoutConfig {
	@Validated
	@ConfigurationProperties("app.redis")
	public record Settings(@NotNull
	Duration commandTimeout, @NotNull
	Duration connectTimeout,
		@PositiveOrZero
		int retryAttempts) {
	}

	@Bean
	public RedissonAutoConfigurationCustomizer boundedRedisCommands(Settings settings) {
		int command = positiveMillis(settings.commandTimeout());
		int connect = positiveMillis(settings.connectTimeout());
		// 현재 local/prod 모두 단일 Redis. 클러스터 전환 시 해당 서버 구성의 timeout도 함께 변경한다.
		return config -> config.useSingleServer().setTimeout(command)
			.setConnectTimeout(connect).setRetryAttempts(settings.retryAttempts());
	}

	private static int positiveMillis(Duration duration) {
		int millis = Math.toIntExact(duration.toMillis());
		if (millis <= 0) {
			throw new IllegalArgumentException("Redis timeout must be positive");
		}
		return millis;
	}
}
