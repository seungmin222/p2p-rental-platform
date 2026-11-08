package com.team7.rental.notification.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.member.entity.Member;
import com.team7.rental.notification.entity.Notification;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.notification.service.NotificationService;
import com.team7.rental.support.IntegrationTestSupport;

/** 알림 저장(중복 방지)과 알림 API 4개 (REQ-032) */
class NotificationIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationRepository notificationRepository;

	private Member me;
	private Member other;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		me = fixture.createMember();
		other = fixture.createMember();
	}

	@Test
	@DisplayName("같은 (회원, event_id)는 두 번 보내도 1건만 저장하고, 다른 회원에게는 같은 event_id로 따로 저장한다")
	void dedupesPerMember() {
		// when
		boolean first = send(me, NotificationType.OVERDUE, 10L, "OVERDUE:10");
		boolean again = send(me, NotificationType.OVERDUE, 10L, "OVERDUE:10");
		boolean otherSide = send(other, NotificationType.OVERDUE, 10L, "OVERDUE:10");

		// then
		assertThat(first).isTrue();
		assertThat(again).isFalse();
		assertThat(otherSide).isTrue();
		assertThat(notificationRepository.findAll()).hasSize(2);
		Notification saved = notificationRepository.findByMemberIdAndEventId(me.getId(), "OVERDUE:10").orElseThrow();
		assertThat(saved.getContent()).isEqualTo("반납 예정일이 지났어요.");
		assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.RENTAL);
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
		assertThat(saved.isRead()).isFalse();
	}

	@Test
	@DisplayName("같은 (회원, event_id)를 동시에 보내도 1건만 저장되고 예외가 없다")
	void concurrentSameKey() throws Exception {
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return send(me, NotificationType.OVERDUE, 10L, "OVERDUE:10");
			}));
		}
		start.countDown();
		int saved = 0;
		for (Future<Boolean> result : results) {
			saved += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
		}
		executor.shutdown();

		assertThat(saved).isEqualTo(1);
		assertThat(notificationRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("저장이 실패해도(없는 회원) 예외를 던지지 않는다")
	void failureIsSwallowed() {
		assertThatCode(() -> notificationService.send(999_999L, NotificationType.OVERDUE,
			NotificationTargetType.RENTAL, 1L, "OVERDUE:1")).doesNotThrowAnyException();
		assertThat(notificationRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("목록은 내 알림만 최신순 20건씩, 다음 커서로 나머지를 준다")
	void listsWithCursor() throws Exception {
		// given: 내 알림 21건 + 다른 회원 1건
		for (long i = 1; i <= 21; i++) {
			send(me, NotificationType.RETURN_REMINDER, i, "RETURN_REMINDER:" + i + ":2026-11-21");
		}
		send(other, NotificationType.OVERDUE, 1L, "OVERDUE:1");

		// when: 첫 페이지
		String body = mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION,
			bearerToken(me.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(20))
			.andExpect(jsonPath("$.data.items[0].targetId").value(21))
			.andExpect(jsonPath("$.data.items[0].type").value("RETURN_REMINDER"))
			.andExpect(jsonPath("$.data.items[0].targetType").value("RENTAL"))
			.andExpect(jsonPath("$.data.items[0].isRead").value(false))
			.andExpect(jsonPath("$.data.items[19].targetId").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andReturn().getResponse().getContentAsString();
		String cursor = JsonPath.read(body, "$.data.nextCursor");

		// then: 다음 페이지
		mockMvc.perform(get("/api/notifications").param("cursor", cursor)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(me.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].targetId").value(1))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextCursor").doesNotExist());
	}

	@Test
	@DisplayName("해석할 수 없는 커서는 400")
	void invalidCursor() throws Exception {
		mockMvc.perform(get("/api/notifications").param("cursor", "!!")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(me.getId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("개별 읽음은 내 알림만(다른 회원 알림은 404), 다시 읽어도 성공하고 안 읽은 수가 줄어든다")
	void markRead() throws Exception {
		// given
		send(me, NotificationType.OVERDUE, 1L, "OVERDUE:1");
		send(me, NotificationType.OVERDUE, 2L, "OVERDUE:2");
		send(other, NotificationType.OVERDUE, 1L, "OVERDUE:1");
		Long mine = notificationRepository.findByMemberIdAndEventId(me.getId(), "OVERDUE:1").orElseThrow().getId();
		Long others = notificationRepository.findByMemberIdAndEventId(other.getId(), "OVERDUE:1").orElseThrow()
			.getId();

		// when & then
		mockMvc.perform(patch("/api/notifications/{id}/read", others).header(HttpHeaders.AUTHORIZATION,
			bearerToken(me.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(patch("/api/notifications/{id}/read", mine).header(HttpHeaders.AUTHORIZATION,
				bearerToken(me.getId())))
				.andExpect(status().isOk());
		}
		mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION,
			bearerToken(me.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.count").value(1));
		assertThat(notificationRepository.findById(others).orElseThrow().isRead()).isFalse();
	}

	@Test
	@DisplayName("모두 읽음은 내 안 읽은 알림만 바꾸고 바꾼 수를 돌려준다")
	void markAllRead() throws Exception {
		// given
		send(me, NotificationType.OVERDUE, 1L, "OVERDUE:1");
		send(me, NotificationType.OVERDUE, 2L, "OVERDUE:2");
		send(other, NotificationType.OVERDUE, 1L, "OVERDUE:1");

		// when & then
		mockMvc.perform(patch("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION,
			bearerToken(me.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.updatedCount").value(2));
		mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION,
			bearerToken(me.getId())))
			.andExpect(jsonPath("$.data.count").value(0));
		mockMvc.perform(get("/api/notifications/unread-count").header(HttpHeaders.AUTHORIZATION,
			bearerToken(other.getId())))
			.andExpect(jsonPath("$.data.count").value(1));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void requiresLogin() throws Exception {
		mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
	}

	private boolean send(Member member, NotificationType type, Long targetId, String eventId) {
		return notificationService.send(member.getId(), type, NotificationTargetType.RENTAL, targetId, eventId);
	}
}
