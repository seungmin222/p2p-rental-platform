package com.team7.rental.chat.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.chat.dto.ChatMessageSendRequest;
import com.team7.rental.chat.dto.ChatReadRequest;
import com.team7.rental.chat.entity.ChatMessageType;
import com.team7.rental.chat.service.ChatMessageService;
import com.team7.rental.chat.service.ChatReadService;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.chat.service.ChatRoomTxService;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 채팅 목록·방 정보·내역·안 읽은 수·읽음 (REQ-028·029), 삭제 물품 방 생성 경합 (REQ-026) */
class ChatListIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 13, 10, 0);

	@Autowired
	private ChatRoomService chatRoomService;

	@Autowired
	private ChatRoomTxService chatRoomTxService;

	@Autowired
	private ChatMessageService chatMessageService;

	@Autowired
	private ChatReadService chatReadService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember verifiedOwner;
	private Member owner;
	private Member renter;
	private Member stranger;
	private Item item;
	private Long roomId;

	@BeforeEach
	void setUp() {
		testClock.fixAt(T0);
		verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		stranger = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		roomId = chatRoomService.create(renter.getId(), item.getId()).room().roomId();
	}

	@Test
	@DisplayName("안 읽은 수는 상대가 보낸 메시지와 SYSTEM 메시지만 세고, 읽음 위치 이후만 센다")
	void unreadCountAccuracy() throws Exception {
		long r1 = send(renter, roomId, "안녕하세요");
		long r2 = send(renter, roomId, "빌릴 수 있나요?");
		send(owner, roomId, "네 가능해요");
		insertSystemMessage(roomId);

		// 소유자: 대여자 2건 + SYSTEM 1건, 자기 메시지 제외
		unreadCount(owner).andExpect(jsonPath("$.data.unreadCount").value(3));
		// 대여자: 소유자 1건 + SYSTEM 1건
		unreadCount(renter).andExpect(jsonPath("$.data.unreadCount").value(2));

		chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(r1));
		unreadCount(owner).andExpect(jsonPath("$.data.unreadCount").value(2));
		chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(r2));
		unreadCount(owner).andExpect(jsonPath("$.data.unreadCount").value(1));

		// 두 번째 방의 안 읽은 수가 전체에 더해진다
		Member another = fixture.createVerifiedMember().member();
		Long secondRoom = chatRoomService.create(another.getId(), item.getId()).room().roomId();
		send(another, secondRoom, "저도 빌리고 싶어요");
		unreadCount(owner).andExpect(jsonPath("$.data.unreadCount").value(2));
		rooms(owner).andExpect(jsonPath("$.data[0].roomId").value(secondRoom))
			.andExpect(jsonPath("$.data[0].unreadCount").value(1))
			.andExpect(jsonPath("$.data[1].roomId").value(roomId))
			.andExpect(jsonPath("$.data[1].unreadCount").value(1));

		// 방이 없는 회원은 0
		unreadCount(stranger).andExpect(jsonPath("$.data.unreadCount").value(0));
	}

	@Test
	@DisplayName("목록은 마지막 메시지 시각 최신순이고 메시지 없는 방은 방 생성 시각으로 끼운다. 상대·물품·미리보기를 담는다")
	void listOrderAndFields() throws Exception {
		Member renter2 = fixture.createVerifiedMember().member();
		Member renter3 = fixture.createVerifiedMember().member();
		testClock.advance(Duration.ofMinutes(1));
		Long room2 = chatRoomService.create(renter2.getId(), item.getId()).room().roomId();
		testClock.advance(Duration.ofMinutes(1));
		send(renter, roomId, "첫 방 메시지"); // T0+2분
		testClock.advance(Duration.ofMinutes(1));
		Long room3 = chatRoomService.create(renter3.getId(), item.getId()).room().roomId(); // T0+3분, 메시지 없음

		rooms(owner).andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].roomId").value(room3))
			.andExpect(jsonPath("$.data[0].lastMessagePreview").doesNotExist())
			.andExpect(jsonPath("$.data[0].unreadCount").value(0))
			.andExpect(jsonPath("$.data[1].roomId").value(roomId))
			.andExpect(jsonPath("$.data[1].lastMessagePreview").value("첫 방 메시지"))
			.andExpect(jsonPath("$.data[1].lastMessageAt").exists())
			.andExpect(jsonPath("$.data[1].counterpart.memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data[1].counterpart.nickname").value(renter.getNickname()))
			.andExpect(jsonPath("$.data[1].counterpart.isWithdrawn").value(false))
			.andExpect(jsonPath("$.data[1].item.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data[1].item.name").value(item.getName()))
			.andExpect(jsonPath("$.data[1].item.isDeleted").value(false))
			.andExpect(jsonPath("$.data[2].roomId").value(room2));

		rooms(renter).andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].counterpart.memberId").value(owner.getId()))
			.andExpect(jsonPath("$.data[0].counterpart.regionName").value(verifiedOwner.region().getName()));
		rooms(stranger).andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("상대가 탈퇴하면 isWithdrawn=true에 닉네임·사진·동네가 비고, 물품이 삭제되면 isDeleted=true로 남는다")
	void withdrawnCounterpartAndDeletedItem() throws Exception {
		jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", owner.getId());
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());

		rooms(renter).andExpect(jsonPath("$.data[0].counterpart.memberId").value(owner.getId()))
			.andExpect(jsonPath("$.data[0].counterpart.isWithdrawn").value(true))
			.andExpect(jsonPath("$.data[0].counterpart.nickname").doesNotExist())
			.andExpect(jsonPath("$.data[0].counterpart.regionName").doesNotExist())
			.andExpect(jsonPath("$.data[0].item.isDeleted").value(true));
		room(renter, roomId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.counterpart.isWithdrawn").value(true))
			.andExpect(jsonPath("$.data.counterpart.nickname").doesNotExist())
			.andExpect(jsonPath("$.data.item.isDeleted").value(true));
	}

	@Test
	@DisplayName("방 정보는 이 (물품, 대여자)의 가장 최근 대여 건(CANCELED 포함)과 상대의 읽음 위치를 담는다")
	void roomDetail() throws Exception {
		room(renter, roomId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.roomId").value(roomId))
			.andExpect(jsonPath("$.data.ownerId").value(owner.getId()))
			.andExpect(jsonPath("$.data.renterId").value(renter.getId()))
			.andExpect(jsonPath("$.data.counterpart.memberId").value(owner.getId()))
			.andExpect(jsonPath("$.data.latestRental").doesNotExist())
			.andExpect(jsonPath("$.data.counterpartLastReadMessageId").doesNotExist());

		rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 3));
		Rental latest = rentalFixture.createRental(item, renter, RentalStatus.CANCELED, LocalDate.of(2026, 11, 20),
			LocalDate.of(2026, 11, 22));
		// 다른 대여자의 대여 건은 이 방과 상관없다
		rentalFixture.createRental(item, stranger, RentalStatus.RESERVED, LocalDate.of(2026, 12, 1),
			LocalDate.of(2026, 12, 2));
		long message = send(renter, roomId, "반납했어요");
		chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(message));

		room(renter, roomId).andExpect(jsonPath("$.data.latestRental.rentalId").value(latest.getId()))
			.andExpect(jsonPath("$.data.latestRental.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.latestRental.startDate").value("2026-11-20"))
			.andExpect(jsonPath("$.data.latestRental.endDate").value("2026-11-22"))
			.andExpect(jsonPath("$.data.counterpartLastReadMessageId").value(message));
		room(owner, roomId).andExpect(jsonPath("$.data.counterpart.memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data.latestRental.rentalId").value(latest.getId()))
			.andExpect(jsonPath("$.data.counterpartLastReadMessageId").doesNotExist());
	}

	@Test
	@DisplayName("비참여자는 방 정보·내역이 403 CHAT_NOT_PARTICIPANT, 없는 방은 404, 비로그인은 401")
	void participantOnly() throws Exception {
		room(stranger, roomId).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHAT_NOT_PARTICIPANT"));
		messages(stranger, roomId, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CHAT_NOT_PARTICIPANT"));
		room(renter, 999_999L).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		messages(renter, 999_999L, null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		mockMvc.perform(get("/api/chat-rooms")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/chat-rooms/unread-count")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("내역은 최신순 50건씩, 마지막 페이지에는 nextCursor가 없다. 정확히 50건이면 한 페이지로 끝난다")
	void historyCursorBoundaries() throws Exception {
		List<Long> ids = insertMessages(roomId, 50);
		MvcResult exact = messages(renter, roomId, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(50))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist())
			.andReturn();
		List<Long> exactIds = itemIds(exact);
		assertThat(exactIds.getFirst()).isEqualTo(ids.getLast());
		assertThat(exactIds.getLast()).isEqualTo(ids.getFirst());

		ids.addAll(insertMessages(roomId, 51)); // 모두 101건
		List<Long> collected = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			MvcResult page = messages(owner, roomId, cursor).andExpect(status().isOk()).andReturn();
			collected.addAll(itemIds(page));
			cursor = JsonPath.read(page.getResponse().getContentAsString(), "$.data.nextCursor");
			boolean hasNext = JsonPath.read(page.getResponse().getContentAsString(), "$.data.hasNext");
			assertThat(hasNext).isEqualTo(cursor != null);
			pages++;
		} while (cursor != null);

		assertThat(pages).isEqualTo(3);
		List<Long> expected = new ArrayList<>(ids);
		Collections.reverse(expected);
		assertThat(collected).containsExactlyElementsOf(expected); // 겹침·빠짐 없이 최신→과거
	}

	@Test
	@DisplayName("해석할 수 없는 커서는 400 COMMON_INVALID_INPUT, 메시지 없는 방은 빈 목록")
	void invalidCursorAndEmpty() throws Exception {
		messages(renter, roomId, null).andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.hasNext").value(false));
		messages(renter, roomId, "not-a-cursor!").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("읽음 위치는 줄어들지 않는다: 작은 값은 무시되고, 동시에 여러 값이 와도 가장 큰 값이 남는다")
	void readNeverDecreases() throws Exception {
		List<Long> ids = insertMessages(roomId, 20);
		chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(ids.get(9)));
		chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(ids.get(4)));
		assertThat(lastRead(owner)).isEqualTo(ids.get(9));

		List<Long> shuffled = new ArrayList<>(ids);
		Collections.shuffle(shuffled);
		ExecutorService executor = Executors.newFixedThreadPool(shuffled.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (Long id : shuffled) {
			futures.add(executor.submit(() -> {
				start.await();
				chatReadService.markRead(renter.getId(), roomId, new ChatReadRequest(id));
				return null;
			}));
		}
		start.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		executor.shutdown();
		assertThat(lastRead(renter)).isEqualTo(ids.getLast());
	}

	@Test
	@DisplayName("읽음은 그 방의 메시지 ID만 받는다: 다른 방·없는 ID·빈 값은 COMMON_INVALID_INPUT, 비참여자는 CHAT_NOT_PARTICIPANT")
	void readValidation() {
		Member another = fixture.createVerifiedMember().member();
		Long otherRoom = chatRoomService.create(another.getId(), item.getId()).room().roomId();
		long otherMessage = send(another, otherRoom, "다른 방");
		long mine = send(renter, roomId, "이 방");

		assertErrorCode(() -> chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(otherMessage)),
			ErrorCode.COMMON_INVALID_INPUT);
		assertErrorCode(() -> chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(mine + 1000)),
			ErrorCode.COMMON_INVALID_INPUT);
		assertErrorCode(() -> chatReadService.markRead(owner.getId(), roomId, new ChatReadRequest(null)),
			ErrorCode.COMMON_INVALID_INPUT);
		assertErrorCode(() -> chatReadService.markRead(stranger.getId(), roomId, new ChatReadRequest(mine)),
			ErrorCode.CHAT_NOT_PARTICIPANT);
		assertErrorCode(() -> chatReadService.markRead(owner.getId(), 999_999L, new ChatReadRequest(mine)),
			ErrorCode.CHAT_ROOM_NOT_FOUND);
		assertThat(lastRead(owner)).isNull();
	}

	@Test
	@DisplayName("삭제 확인 뒤 물품이 삭제되면 방 insert 트랜잭션이 404 ITEM_NOT_FOUND로 롤백된다(방·참여자 없음, 채팅 수 그대로)")
	void deletedBetweenCheckAndInsert() {
		Member another = fixture.createVerifiedMember().member();
		int before = chatCount();
		long roomsBefore = count("chat_rooms");
		long membersBefore = count("chat_room_members");
		// ChatRoomService.create의 삭제 확인을 통과한 직후 삭제가 커밋된 상황
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());

		assertErrorCode(() -> chatRoomTxService.open(item.getId(), another.getId(), owner.getId()),
			ErrorCode.ITEM_NOT_FOUND);
		assertThat(count("chat_rooms")).isEqualTo(roomsBefore);
		assertThat(count("chat_room_members")).isEqualTo(membersBefore);
		assertThat(chatCount()).isEqualTo(before);
	}

	private long send(Member sender, Long room, String content) {
		return chatMessageService.send(sender.getId(), room,
			new ChatMessageSendRequest(ChatMessageType.TEXT, content, null)).messageId();
	}

	private void insertSystemMessage(Long room) {
		jdbcTemplate.update("INSERT INTO chat_messages (room_id, sender_id, type, content, sent_at, created_at, "
			+ "updated_at) VALUES (?, NULL, 'SYSTEM', '예약이 확정되었습니다', NOW(6), NOW(6), NOW(6))", room);
	}

	/** 메시지 n건을 순서대로 넣고 id 오름차순 목록을 돌려준다 */
	private List<Long> insertMessages(Long room, int n) {
		for (int i = 0; i < n; i++) {
			Long sender = i % 2 == 0 ? renter.getId() : owner.getId();
			jdbcTemplate.update("INSERT INTO chat_messages (room_id, sender_id, type, content, sent_at, created_at, "
				+ "updated_at) VALUES (?, ?, 'TEXT', ?, NOW(6), NOW(6), NOW(6))", room, sender, "m" + i);
		}
		List<Long> all = jdbcTemplate.queryForList("SELECT id FROM chat_messages WHERE room_id = ? ORDER BY id",
			Long.class, room);
		return new ArrayList<>(all.subList(all.size() - n, all.size()));
	}

	private List<Long> itemIds(MvcResult result) throws Exception {
		List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items[*].messageId");
		return ids.stream().map(Number::longValue).toList();
	}

	private Long lastRead(Member member) {
		return jdbcTemplate.queryForObject(
			"SELECT last_read_message_id FROM chat_room_members WHERE room_id = ? AND member_id = ?", Long.class,
			roomId, member.getId());
	}

	private int chatCount() {
		return jdbcTemplate.queryForObject("SELECT chat_count FROM items WHERE id = ?", Integer.class, item.getId());
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}

	private void assertErrorCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(expected);
	}

	private ResultActions rooms(Member member) throws Exception {
		return mockMvc.perform(get("/api/chat-rooms").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions room(Member member, Long room) throws Exception {
		return mockMvc.perform(
			get("/api/chat-rooms/{id}", room).header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ResultActions messages(Member member, Long room, String cursor) throws Exception {
		var request = get("/api/chat-rooms/{id}/messages", room).header(HttpHeaders.AUTHORIZATION,
			bearerToken(member.getId()));
		if (cursor != null) {
			request = request.param("cursor", cursor);
		}
		return mockMvc.perform(request);
	}

	private ResultActions unreadCount(Member member) throws Exception {
		return mockMvc.perform(
			get("/api/chat-rooms/unread-count").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}
}
