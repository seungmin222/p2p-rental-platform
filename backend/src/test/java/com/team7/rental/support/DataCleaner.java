package com.team7.rental.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 사이에 데이터가 남지 않게 MySQL 테이블을 TRUNCATE하고 Redis를 비운다.
 * 롤백(@Transactional) 대신 이 방식을 쓰는 이유: 동시성 테스트처럼 다른 스레드에서 커밋된 데이터도 지워야 해서.
 */
@TestComponent
class DataCleaner {

	/**
	 * 지우지 않는 테이블. Flyway 이력과, 버전 마이그레이션(V)으로 넣는 시드 테이블(카테고리·차감 정책 등)을
	 * 해당 마이그레이션을 추가할 때 여기에 함께 등록한다.
	 */
	private static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history", "categories");

	private final JdbcTemplate jdbcTemplate;
	private final RedisConnectionFactory redisConnectionFactory;

	DataCleaner(JdbcTemplate jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
		this.jdbcTemplate = jdbcTemplate;
		this.redisConnectionFactory = redisConnectionFactory;
	}

	void clean() {
		truncateTables();
		flushRedis();
	}

	private void truncateTables() {
		List<String> tables = jdbcTemplate.queryForList(
			"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
			String.class).stream().filter(table -> !EXCLUDED_TABLES.contains(table)).toList();
		if (tables.isEmpty()) {
			return;
		}
		// FOREIGN_KEY_CHECKS는 세션 단위라 같은 커넥션에서 실행되도록 한 번의 batchUpdate로 묶는다
		List<String> statements = new ArrayList<>();
		statements.add("SET FOREIGN_KEY_CHECKS = 0");
		tables.forEach(table -> statements.add("TRUNCATE TABLE `" + table + "`"));
		statements.add("SET FOREIGN_KEY_CHECKS = 1");
		jdbcTemplate.batchUpdate(statements.toArray(String[]::new));
	}

	private void flushRedis() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}
}
