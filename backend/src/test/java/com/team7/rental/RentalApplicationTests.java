package com.team7.rental;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.support.IntegrationTestSupport;

class RentalApplicationTests extends IntegrationTestSupport {

	@Autowired
	private Clock clock;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {}

	@Test
	@DisplayName("Clock 빈과 JVM 기본 타임존은 Asia/Seoul이다")
	void timeZoneIsSeoul() {
		// then
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
		assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

	@Test
	@DisplayName("Flyway가 테스트 DB에 적용돼 대기 중인 마이그레이션이 없다")
	void flywayApplied() {
		// then
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'",
			Integer.class)).isEqualTo(1);
	}
}
