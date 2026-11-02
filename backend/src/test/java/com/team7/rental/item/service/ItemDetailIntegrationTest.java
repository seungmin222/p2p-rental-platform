package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.item.repository.ItemViewRepository;
import com.team7.rental.support.IntegrationTestSupport;

class ItemDetailIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private ItemRepository items;
	@Autowired
	private ItemImageRepository images;
	@Autowired
	private ItemViewRepository views;
	@Autowired
	private StringRedisTemplate redis;
	@Autowired
	private ItemViewFlushService flushService;
	@Autowired
	private ItemViewWriter writer;
	private Item item;
	private Long ownerId;
	private Long visitorId;

	@BeforeEach
	void setUp() {
		testClock.fixAt(LocalDateTime.of(2026, 11, 2, 12, 0));
		var owner = fixture.createVerifiedMember();
		ownerId = owner.member().getId();
		visitorId = fixture.createMember().getId();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 8)).item();
		images.saveAndFlush(ItemImage.create(item.getId(), "/files/second.png", "/files/second-thumb.png", 1));
		images.saveAndFlush(ItemImage.create(item.getId(), "/files/first.png", "/files/first-thumb.png", 0));
	}

	@Test
	void publicDetailReturnsOrderedImagesAndNeverExposesItemPrice() throws Exception {
		mockMvc.perform(get("/api/items/{id}", item.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.images[0].url").value("/files/first.png"))
			.andExpect(jsonPath("$.data.images[1].url").value("/files/second.png"))
			.andExpect(jsonPath("$.data.owner.memberId").value(ownerId))
			.andExpect(jsonPath("$.data.owner.nickname").isString())
			.andExpect(jsonPath("$.data.regionName").isString())
			.andExpect(jsonPath("$.data.itemPrice").doesNotExist())
			.andExpect(jsonPath("$.data.isOwner").value(false))
			.andExpect(jsonPath("$.data.isWished").value(false))
			.andExpect(jsonPath("$.data.viewCount").value(1));
	}

	@Test
	void hiddenItemIsOnlyVisibleToOwner() throws Exception {
		setStatus(ItemStatus.HIDDEN);
		mockMvc.perform(get("/api/items/{id}", item.getId())).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/items/{id}", item.getId()).header(HttpHeaders.AUTHORIZATION, bearerToken(visitorId)))
			.andExpect(status().isNotFound());
		assertThat(views.snapshot(item.getId())).isNull();
		mockMvc.perform(get("/api/items/{id}", item.getId()).header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.isOwner").value(true));
	}

	@Test
	void deletedAndUnknownItemsReturn404WithoutCounting() throws Exception {
		setStatus(ItemStatus.DELETED);
		mockMvc.perform(get("/api/items/{id}", item.getId()).header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId)))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/items/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
		assertThat(views.snapshot(item.getId())).isNull();
	}

	@Test
	void authenticatedMemberIsDeduplicatedAcrossDifferentIps() throws Exception {
		for (String address : new String[] {"192.0.2.1", "192.0.2.2"}) {
			mockMvc.perform(get("/api/items/{id}", item.getId())
				.with(request -> {
					request.setRemoteAddr(address);
					return request;
				})
				.header(HttpHeaders.AUTHORIZATION, bearerToken(visitorId)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.viewCount").value(1));
		}
	}

	@Test
	void anonymousIpDedupeExpiresAfterConfigured24Hours() {
		assertThat(views.record(item.getId(), null, "192.0.2.1", 0)).isEqualTo(1);
		assertThat(views.record(item.getId(), null, "192.0.2.1", 0)).isEqualTo(1);
		String key = ItemViewRepository.dedupeKey(item.getId(), null, "192.0.2.1");
		assertThat(redis.getExpire(key)).isBetween(86390L, 86400L);
		assertThat(views.record(item.getId(), null, "192.0.2.2", 0)).isEqualTo(2);
		// 실제 24시간을 기다리는 대신 Redis TTL 만료를 재현한다. Clock은 Redis 시간을 바꾸지 않는다.
		redis.expire(key, Duration.ZERO);
		assertThat(views.record(item.getId(), null, "192.0.2.1", 0)).isEqualTo(3);
	}

	@Test
	void simultaneousViewsFromSameMemberIncrementOnce() throws Exception {
		try (var executor = Executors.newFixedThreadPool(8)) {
			var work = new ArrayList<Callable<Integer>>();
			for (int i = 0; i < 24; i++) {
				work.add(() -> views.record(item.getId(), visitorId, "192.0.2.1", 0));
			}
			for (var result : executor.invokeAll(work)) {
				assertThat(result.get()).isEqualTo(1);
			}
		}
		assertThat(views.snapshot(item.getId())).isEqualTo(1);
	}

	@Test
	void databaseFlushIsIdempotentEvenWhenAcknowledgementIsRetried() {
		views.record(item.getId(), visitorId, "192.0.2.1", 0);
		// DB 커밋 후 Redis ACK 전에 프로세스가 종료된 상황을 재현한다.
		writer.flush(item.getId(), 1);
		flushService.flushPending();
		flushService.flushPending();
		assertThat(items.findById(item.getId()).orElseThrow().getViewCount()).isEqualTo(1);
	}

	@Test
	void newViewDuringFlushRemainsPendingAndOldSnapshotCannotDecreaseCount() {
		views.record(item.getId(), visitorId, "192.0.2.1", 0);
		writer.flush(item.getId(), 1);
		views.record(item.getId(), ownerId, "192.0.2.1", 1);
		views.acknowledge(item.getId(), 1);
		flushService.flushPending();
		writer.flush(item.getId(), 1);
		assertThat(items.findById(item.getId()).orElseThrow().getViewCount()).isEqualTo(2);
	}

	private void setStatus(ItemStatus status) {
		ReflectionTestUtils.setField(item, "status", status);
		items.saveAndFlush(item);
	}
}
