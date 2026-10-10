package com.team7.rental.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** TimeConfig의 Clock 대신 {@link TestClock}이 주입되게 한다. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {

	@Bean
	@Primary
	TestClock testClock() {
		return new TestClock();
	}
}
