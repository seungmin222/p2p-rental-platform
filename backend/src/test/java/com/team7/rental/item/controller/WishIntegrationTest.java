package com.team7.rental.item.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
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

/** 관심(찜) (REQ-022): 멱등 추가·해제, 관심 수, 본인·삭제·숨김 물품, 동시 추가, 관심 목록, 상세 isWished */
class WishIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 12, 31);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember verifiedOwner;
	private Member owner;
	private Member member;
	private Item item;

	@BeforeEach
	void setUp() {
		verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		member = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, FROM, TO).item();
	}

	@Test
	@DisplayName("추가하면 관심 수가 1 오르고, 다시 추가해도 그대로다")
	void addIsIdempotent() throws Exception {
		add(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.isWished").value(true))
			.andExpect(jsonPath("$.data.wishCount").value(1));

		add(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isWished").value(true))
			.andExpect(jsonPath("$.data.wishCount").value(1));

		assertThat(wishCount()).isEqualTo(1);
		assertThat(wishRows()).isEqualTo(1);
	}

	@Test
	@DisplayName("다른 회원이 추가하면 관심 수가 따로 오른다")
	void otherMemberAdds() throws Exception {
		Member another = fixture.createVerifiedMember().member();
		add(member, item.getId()).andExpect(jsonPath("$.data.wishCount").value(1));
		add(another, item.getId()).andExpect(jsonPath("$.data.wishCount").value(2));

		assertThat(wishCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("해제하면 관심 수가 1 내려가고, 관심이 아닐 때 해제해도 그대로다(음수 없음)")
	void removeIsIdempotent() throws Exception {
		add(member, item.getId());

		remove(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isWished").value(false))
			.andExpect(jsonPath("$.data.wishCount").value(0));
		remove(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isWished").value(false))
			.andExpect(jsonPath("$.data.wishCount").value(0));

		assertThat(wishCount()).isZero();
		assertThat(wishRows()).isZero();
	}

	@Test
	@DisplayName("관심 행이 있는데 카운터가 0이어도 해제는 0 아래로 내리지 않는다")
	void removeNeverGoesNegative() throws Exception {
		add(member, item.getId());
		jdbcTemplate.update("UPDATE items SET wish_count = 0 WHERE id = ?", item.getId());

		remove(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.wishCount").value(0));

		assertThat(wishCount()).isZero();
	}

	@Test
	@DisplayName("본인 물품은 400 WISH_OWN_ITEM, 숨김 상태여도 같다")
	void ownItem() throws Exception {
		add(owner, item.getId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("WISH_OWN_ITEM"));

		setStatus("HIDDEN");
		add(owner, item.getId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("WISH_OWN_ITEM"));
	}

	@Test
	@DisplayName("없는 물품, 삭제된 물품, 남의 숨김 물품에 추가하면 404 ITEM_NOT_FOUND")
	void addToInvisibleItem() throws Exception {
		add(member, 999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

		setStatus("HIDDEN");
		add(member, item.getId()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

		setStatus("DELETED");
		add(member, item.getId()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

		assertThat(wishCount()).isZero();
		assertThat(wishRows()).isZero();
	}

	@Test
	@DisplayName("삭제·숨김 물품도 해제할 수 있고, 없는 물품 해제만 404다")
	void removeFromDeletedOrHiddenItem() throws Exception {
		add(member, item.getId());
		setStatus("DELETED");
		remove(member, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isWished").value(false));
		assertThat(wishRows()).isZero();

		setStatus("AVAILABLE");
		add(member, item.getId());
		setStatus("HIDDEN");
		remove(member, item.getId()).andExpect(status().isOk());
		assertThat(wishRows()).isZero();

		remove(member, 999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
	}

	@Test
	@DisplayName("비로그인은 추가·해제·목록 모두 401")
	void unauthenticated() throws Exception {
		mockMvc.perform(post("/api/items/{itemId}/wishes", item.getId())).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/items/{itemId}/wishes", item.getId())).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/members/me/wishes")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("같은 회원이 동시에 10번 추가해도 관심 행은 1개, 관심 수는 1이다")
	void concurrentAdd() throws Exception {
		int threads = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<MvcResult> task = () -> {
				ready.countDown();
				start.await();
				return add(member, item.getId()).andReturn();
			};
			futures.add(executor.submit(task));
		}
		ready.await();
		start.countDown();

		List<Integer> statuses = new ArrayList<>();
		for (Future<MvcResult> future : futures) {
			statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
		}
		executor.shutdown();

		assertThat(statuses).containsOnly(200);
		assertThat(wishRows()).isEqualTo(1);
		assertThat(wishCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("서로 다른 회원 10명이 동시에 추가하면 관심 수는 10이다")
	void concurrentAddByDifferentMembers() throws Exception {
		int threads = 10;
		List<Member> members = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			members.add(fixture.createVerifiedMember().member());
		}
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (Member each : members) {
			Callable<MvcResult> task = () -> {
				ready.countDown();
				start.await();
				return add(each, item.getId()).andReturn();
			};
			futures.add(executor.submit(task));
		}
		ready.await();
		start.countDown();
		for (Future<MvcResult> future : futures) {
			assertThat(future.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		}
		executor.shutdown();

		assertThat(wishRows()).isEqualTo(threads);
		assertThat(wishCount()).isEqualTo(threads);
	}

	@Test
	@DisplayName("관심 목록은 최근 추가순이고 삭제·숨김 물품도 status와 함께 남는다")
	void listShowsLatestFirstWithStatus() throws Exception {
		Item second = fixture.createItem(verifiedOwner, FROM, TO).item();
		Item third = fixture.createItem(verifiedOwner, FROM, TO).item();
		add(member, item.getId());
		add(member, third.getId());
		add(member, second.getId());
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", third.getId());
		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", second.getId());

		list(member, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(3))
			.andExpect(jsonPath("$.data.items[0].itemId").value(second.getId()))
			.andExpect(jsonPath("$.data.items[0].status").value("HIDDEN"))
			.andExpect(jsonPath("$.data.items[1].itemId").value(third.getId()))
			.andExpect(jsonPath("$.data.items[1].status").value("DELETED"))
			.andExpect(jsonPath("$.data.items[2].itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.items[2].status").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.items[2].name").value(item.getName()))
			.andExpect(jsonPath("$.data.items[2].regionName").exists())
			.andExpect(jsonPath("$.data.items[2].wishCount").value(1))
			.andExpect(jsonPath("$.data.items[2].isRenting").value(false))
			.andExpect(jsonPath("$.data.items[2].wishedAt").exists())
			.andExpect(jsonPath("$.data.items[2].itemPrice").doesNotExist())
			.andExpect(jsonPath("$.data.totalElements").value(3));

		list(owner, null).andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	@DisplayName("관심 목록 페이지 크기는 wish.page(20) 고정이고 size를 받지 않는다")
	void listPageSizeIsFixed() throws Exception {
		for (int i = 0; i < 21; i++) {
			add(member, fixture.createItem(verifiedOwner, FROM, TO).item().getId());
		}

		mockMvc.perform(get("/api/members/me/wishes").param("size", "5")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(21));
		list(member, 1).andExpect(jsonPath("$.data.items.length()").value(1));
		list(member, -1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("상세 isWished: 관심 회원은 true, 해제 후·다른 회원·비회원·제공자는 false")
	void detailIsWished() throws Exception {
		add(member, item.getId());
		detail(member).andExpect(jsonPath("$.data.isWished").value(true))
			.andExpect(jsonPath("$.data.wishCount").value(1));
		detail(fixture.createVerifiedMember().member()).andExpect(jsonPath("$.data.isWished").value(false));
		detail(owner).andExpect(jsonPath("$.data.isWished").value(false));
		mockMvc.perform(get("/api/items/{itemId}", item.getId()))
			.andExpect(jsonPath("$.data.isWished").value(false));

		remove(member, item.getId());
		detail(member).andExpect(jsonPath("$.data.isWished").value(false));
	}

	private ResultActions add(Member who, Long itemId) throws Exception {
		return mockMvc.perform(post("/api/items/{itemId}/wishes", itemId)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId())));
	}

	private ResultActions remove(Member who, Long itemId) throws Exception {
		return mockMvc.perform(delete("/api/items/{itemId}/wishes", itemId)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId())));
	}

	private ResultActions list(Member who, Integer page) throws Exception {
		var request = get("/api/members/me/wishes").header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId()));
		if (page != null) {
			request.param("page", page.toString());
		}
		return mockMvc.perform(request);
	}

	private ResultActions detail(Member who) throws Exception {
		return mockMvc.perform(get("/api/items/{itemId}", item.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(who.getId())));
	}

	private void setStatus(String status) {
		jdbcTemplate.update("UPDATE items SET status = ? WHERE id = ?", status, item.getId());
	}

	private int wishCount() {
		return jdbcTemplate.queryForObject("SELECT wish_count FROM items WHERE id = ?", Integer.class, item.getId());
	}

	private long wishRows() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wishes WHERE item_id = ?", Long.class, item.getId());
	}
}
