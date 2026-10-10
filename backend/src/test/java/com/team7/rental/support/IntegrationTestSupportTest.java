package com.team7.rental.support;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class IntegrationTestSupportTest extends IntegrationTestSupport {

	@Autowired
	private Clock clock;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private DataCleaner dataCleaner;

	@Test
	@DisplayName("주입받은 Clock은 testClock으로 고정·이동할 수 있다")
	void fixClock() {
		// given
		LocalDateTime fixed = LocalDateTime.of(2026, 5, 1, 10, 0);

		// when
		testClock.fixAt(fixed);
		testClock.advance(Duration.ofDays(1));

		// then
		assertThat(LocalDateTime.now(clock)).isEqualTo(fixed.plusDays(1));
	}

	@Test
	@DisplayName("정리하면 MySQL 테이블 데이터와 Redis 키가 지워지고 Flyway 이력은 남는다")
	void cleanData() {
		// given
		jdbcTemplate.execute("CREATE TABLE cleaner_parent (id BIGINT PRIMARY KEY)");
		jdbcTemplate.execute("CREATE TABLE cleaner_child (id BIGINT PRIMARY KEY, parent_id BIGINT,"
			+ " FOREIGN KEY (parent_id) REFERENCES cleaner_parent (id))");
		try {
			jdbcTemplate.update("INSERT INTO cleaner_parent VALUES (1)");
			jdbcTemplate.update("INSERT INTO cleaner_child VALUES (1, 1)");
			redisTemplate.opsForValue().set("cleaner:key", "value");
			int historyRows = countRows("flyway_schema_history");

			// when
			dataCleaner.clean();

			// then
			assertThat(countRows("cleaner_parent")).isZero();
			assertThat(countRows("cleaner_child")).isZero();
			assertThat(redisTemplate.hasKey("cleaner:key")).isFalse();
			assertThat(countRows("flyway_schema_history")).isEqualTo(historyRows);
		} finally {
			jdbcTemplate.execute("DROP TABLE cleaner_child");
			jdbcTemplate.execute("DROP TABLE cleaner_parent");
		}
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}
}
