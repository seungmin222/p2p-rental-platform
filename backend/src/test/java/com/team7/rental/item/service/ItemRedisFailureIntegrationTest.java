package com.team7.rental.item.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team7.rental.item.repository.ItemViewRepository;
import com.team7.rental.support.IntegrationTestSupport;

class ItemRedisFailureIntegrationTest extends IntegrationTestSupport {
	@MockitoBean
	private ItemViewRepository views;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private org.redisson.api.RedissonClient redisson;
	private Long itemId;

	@BeforeEach
	void setUp() {
		var owner = fixture.createVerifiedMember();
		itemId = fixture.createItem(owner, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 8)).item().getId();
		jdbc.update("UPDATE items SET view_count = 17 WHERE id = ?", itemId);
	}

	@Test
	void connectionFailureReturnsDatabaseViewCount() throws Exception {
		when(views.record(eq(itemId), isNull(), anyString(), eq(17)))
			.thenThrow(new RedisConnectionFailureException("unavailable"));
		assertFallback();
	}

	@Test
	void commandTimeoutReturnsDatabaseViewCount() throws Exception {
		when(views.record(eq(itemId), isNull(), anyString(), eq(17)))
			.thenThrow(new QueryTimeoutException("timeout"));
		assertFallback();
	}

	@Test
	void runningClientUsesConfiguredTimeoutsAndNoRetries() {
		var config = redisson.getConfig().useSingleServer();
		org.assertj.core.api.Assertions.assertThat(config.getTimeout()).isEqualTo(1000);
		org.assertj.core.api.Assertions.assertThat(config.getConnectTimeout()).isEqualTo(1000);
		org.assertj.core.api.Assertions.assertThat(config.getRetryAttempts()).isZero();
	}

	private void assertFallback() throws Exception {
		mockMvc.perform(get("/api/items/{id}", itemId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemId").value(itemId))
			.andExpect(jsonPath("$.data.viewCount").value(17));
	}
}
