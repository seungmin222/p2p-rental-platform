package com.team7.rental.chat.controller;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 채팅방 생성 (REQ-026): 기존 방 반환, 채팅 수, 본인·삭제·숨김 물품, 동시 생성 */
class ChatRoomIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Member owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
	}

	@Test
	@DisplayName("처음이면 201로 방을 만들고, 다시 요청하면 같은 방을 200으로 돌려준다. 채팅 수는 한 번만 오른다")
	void createThenReturnExisting() throws Exception {
		MvcResult created = create(renter, item.getId()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.roomId").exists())
			.andExpect(jsonPath("$.data.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.ownerId").value(owner.getId()))
			.andExpect(jsonPath("$.data.renterId").value(renter.getId()))
			.andReturn();
		long roomId = roomId(created);

		create(renter, item.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.roomId").value(roomId));

		assertThat(chatCount()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList(
			"SELECT member_id FROM chat_room_members WHERE room_id = ? AND last_read_message_id IS NULL ORDER BY member_id",
			Long.class, roomId)).containsExactlyInAnyOrder(owner.getId(), renter.getId());
	}

	@Test
	@DisplayName("다른 대여 희망자는 같은 물품에 자기 방을 따로 만든다")
	void otherRenterGetsOwnRoom() throws Exception {
		Member another = fixture.createVerifiedMember().member();
		long first = roomId(create(renter, item.getId()).andExpect(status().isCreated()).andReturn());
		long second = roomId(create(another, item.getId()).andExpect(status().isCreated()).andReturn());

		assertThat(second).isNotEqualTo(first);
		assertThat(chatCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("본인 물품이면 400 CHAT_OWN_ITEM")
	void ownItem() throws Exception {
		create(owner, item.getId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("CHAT_OWN_ITEM"));
	}

	@Test
	@DisplayName("없거나 삭제된 물품은 404 ITEM_NOT_FOUND, 숨김 물품은 만들 수 있다")
	void deletedAndHiddenItem() throws Exception {
		create(renter, 999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());
		create(renter, item.getId()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));

		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", item.getId());
		create(renter, item.getId()).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("itemId가 없으면 400, 비로그인은 401")
	void invalidAndUnauthenticated() throws Exception {
		mockMvc.perform(post("/api/chat-rooms").header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(post("/api/chat-rooms").contentType(MediaType.APPLICATION_JSON)
			.content("{\"itemId\":" + item.getId() + "}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("같은 회원이 동시에 10번 요청해도 방은 1개, 채팅 수는 1이다")
	void concurrentCreate() throws Exception {
		int threads = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<MvcResult>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<MvcResult> task = () -> {
				ready.countDown();
				start.await();
				return create(renter, item.getId()).andReturn();
			};
			futures.add(executor.submit(task));
		}
		ready.await();
		start.countDown();

		List<Integer> statuses = new ArrayList<>();
		List<Long> roomIds = new ArrayList<>();
		for (Future<MvcResult> future : futures) {
			MvcResult result = future.get(30, TimeUnit.SECONDS);
			statuses.add(result.getResponse().getStatus());
			roomIds.add(roomId(result));
		}
		executor.shutdown();

		assertThat(statuses).containsOnly(200, 201);
		assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
		assertThat(roomIds).containsOnly(roomIds.getFirst());
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_rooms", Long.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_room_members", Long.class)).isEqualTo(2);
		assertThat(chatCount()).isEqualTo(1);
	}

	private ResultActions create(Member member, Long itemId) throws Exception {
		return mockMvc.perform(post("/api/chat-rooms").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"itemId\":" + itemId + "}"));
	}

	private long roomId(MvcResult result) throws Exception {
		Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.roomId");
		return id.longValue();
	}

	private int chatCount() {
		return jdbcTemplate.queryForObject("SELECT chat_count FROM items WHERE id = ?", Integer.class, item.getId());
	}
}
