package com.team7.rental.item.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 최근 본 상품 (REQ-035): 상세 조회 기록·갱신, 비회원·본인 제외, 20건 유지, 목록 순서·삭제·숨김 제외, 기록 실패 */
class RecentItemIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 12, 31);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember verifiedOwner;
	private Member owner;
	private Member member;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		member = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, FROM, TO).item();
	}

	@Test
	@DisplayName("상세를 보면 기록되고, 다시 보면 행은 그대로 1개이고 viewed_at만 갱신된다")
	void detailRecordsAndUpdates() throws Exception {
		detail(member, item.getId()).andExpect(status().isOk());
		assertThat(viewedAt(member, item.getId())).isEqualTo(NOW);

		testClock.advance(Duration.ofHours(1));
		detail(member, item.getId()).andExpect(status().isOk());

		assertThat(rows(member)).isEqualTo(1);
		assertThat(viewedAt(member, item.getId())).isEqualTo(NOW.plusHours(1));
	}

	@Test
	@DisplayName("비회원 조회와 본인 물품 조회(숨김 포함)는 기록하지 않는다")
	void guestAndOwnerNotRecorded() throws Exception {
		mockMvc.perform(get("/api/items/{itemId}", item.getId())).andExpect(status().isOk());
		detail(owner, item.getId()).andExpect(status().isOk());
		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", item.getId());
		detail(owner, item.getId()).andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_view_logs", Long.class)).isZero();
	}

	@Test
	@DisplayName("볼 수 없는 물품(404)은 기록하지 않는다")
	void invisibleNotRecorded() throws Exception {
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());
		detail(member, item.getId()).andExpect(status().isNotFound());

		assertThat(rows(member)).isZero();
	}

	@Test
	@DisplayName("21개 물품을 보면 가장 오래 본 1건이 지워지고 최신 20건만 남는다")
	void keepsLatestTwenty() throws Exception {
		List<Long> itemIds = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			Long itemId = fixture.createItem(verifiedOwner, FROM, TO).item().getId();
			itemIds.add(itemId);
			detail(member, itemId).andExpect(status().isOk());
			testClock.advance(Duration.ofMinutes(1));
		}

		assertThat(rows(member)).isEqualTo(20);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM item_view_logs WHERE member_id = ? AND item_id = ?", Long.class, member.getId(),
			itemIds.getFirst())).isZero();
		recentItems(member).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(20))
			.andExpect(jsonPath("$.data[0].itemId").value(itemIds.getLast()));
	}

	@Test
	@DisplayName("다시 본 물품은 맨 앞으로 오고 정리 대상에서 빠진다")
	void reviewMovesToFront() throws Exception {
		Item second = fixture.createItem(verifiedOwner, FROM, TO).item();
		detail(member, item.getId());
		testClock.advance(Duration.ofMinutes(1));
		detail(member, second.getId());
		testClock.advance(Duration.ofMinutes(1));
		detail(member, item.getId());

		recentItems(member).andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].itemId").value(item.getId()))
			.andExpect(jsonPath("$.data[1].itemId").value(second.getId()));
	}

	@Test
	@DisplayName("목록은 최근 본 순 피드 카드이고 삭제·숨김 물품은 빠진다. 다른 회원 기록은 보이지 않는다")
	void listExcludesDeletedAndHidden() throws Exception {
		Item deleted = fixture.createItem(verifiedOwner, FROM, TO).item();
		Item hidden = fixture.createItem(verifiedOwner, FROM, TO).item();
		detail(member, item.getId());
		testClock.advance(Duration.ofMinutes(1));
		detail(member, deleted.getId());
		testClock.advance(Duration.ofMinutes(1));
		detail(member, hidden.getId());
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", deleted.getId());
		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", hidden.getId());

		recentItems(member).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].itemId").value(item.getId()))
			.andExpect(jsonPath("$.data[0].name").value(item.getName()))
			.andExpect(jsonPath("$.data[0].regionName").exists())
			.andExpect(jsonPath("$.data[0].isRenting").value(false))
			.andExpect(jsonPath("$.data[0].itemPrice").doesNotExist());
		recentItems(fixture.createVerifiedMember().member()).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("기록이 실패해도(FK 위반) 상세는 200이다")
	void recordFailureDoesNotBreakDetail() throws Exception {
		// 회원 행이 없는 토큰: 상세 조회는 회원 행을 읽지 않고, 기록 insert만 FK 위반으로 실패한다
		mockMvc.perform(get("/api/items/{itemId}", item.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(999_999L)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemId").value(item.getId()));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_view_logs", Long.class)).isZero();
	}

	@Test
	@DisplayName("같은 회원이 같은 물품을 동시에 10번 봐도 기록은 1행이다")
	void concurrentViews() throws Exception {
		int threads = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<MvcResult> task = () -> {
				ready.countDown();
				start.await();
				return detail(member, item.getId()).andReturn();
			};
			futures.add(executor.submit(task));
		}
		ready.await();
		start.countDown();
		for (Future<MvcResult> future : futures) {
			assertThat(future.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		}
		executor.shutdown();

		assertThat(rows(member)).isEqualTo(1);
	}

	@Test
	@DisplayName("비로그인은 401")
	void unauthenticated() throws Exception {
		mockMvc.perform(get("/api/members/me/recent-items")).andExpect(status().isUnauthorized());
	}

	private ResultActions detail(Member who, Long itemId) throws Exception {
		return mockMvc.perform(get("/api/items/{itemId}", itemId)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId())));
	}

	private ResultActions recentItems(Member who) throws Exception {
		return mockMvc.perform(get("/api/members/me/recent-items")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId())));
	}

	private long rows(Member who) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_view_logs WHERE member_id = ?", Long.class,
			who.getId());
	}

	private LocalDateTime viewedAt(Member who, Long itemId) {
		return jdbcTemplate.queryForObject(
			"SELECT viewed_at FROM item_view_logs WHERE member_id = ? AND item_id = ?", LocalDateTime.class,
			who.getId(), itemId);
	}
}
