package com.team7.rental.global.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

class RedisTimeoutConfigTest {
	@Test
	void appliesCommandAndConnectionTimeoutWithoutRetries() {
		var config = new Config();
		var server = config.useSingleServer().setAddress("redis://127.0.0.1:6379");
		var settings = new RedisTimeoutConfig.Settings(Duration.ofSeconds(1), Duration.ofSeconds(1), 0);
		new RedisTimeoutConfig().boundedRedisCommands(settings).customize(config);
		assertThat(server.getTimeout()).isEqualTo(1000);
		assertThat(server.getConnectTimeout()).isEqualTo(1000);
		assertThat(server.getRetryAttempts()).isZero();
	}
}
