package com.team7.rental.chat.controller;

import static com.team7.rental.support.StompTestClient.*;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.chat.dto.ChatErrorResponse;
import com.team7.rental.chat.dto.ChatMessageResponse;
import com.team7.rental.chat.dto.ChatRoomUpdateResponse;
import com.team7.rental.chat.entity.ChatMessageType;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.notification.dto.NotificationResponse;
import com.team7.rental.notification.entity.NotificationTargetType;
import com.team7.rental.notification.entity.NotificationType;
import com.team7.rental.notification.service.NotificationService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;
import com.team7.rental.support.StompTestClient;
import com.team7.rental.support.StompTestClient.Connection;

/**
 * 실시간 메시지 송수신 (REQ-027): 실제 서버 포트 + RabbitMQ STOMP 브로커 릴레이.
 * CONNECT 인증, 비참여자 SUBSCRIBE·SEND 거부, 송수신, 목록 갱신 이벤트, 알림 실시간 전달(REQ-032).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStompIntegrationTest extends IntegrationTestSupport {

	@LocalServerPort
	private int port;

	@Autowired
	private ChatRoomService chatRoomService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Member owner;
	private Member renter;
	private Member stranger;
	private Long roomId;
	private final java.util.List<Connection> connections = new java.util.ArrayList<>();

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		stranger = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		roomId = chatRoomService.create(renter.getId(), item.getId()).room().roomId();
	}

	@AfterEach
	void disconnectAll() {
		connections.forEach(Connection::disconnect);
	}

	@Test
	@DisplayName("CONNECT에 토큰이 없거나 잘못되면 ERROR(AUTH_UNAUTHORIZED)로 거부한다")
	void connectRequiresToken() throws Exception {
		Connection noToken = connect(null);
		assertThat(noToken.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_UNAUTHORIZED");
		assertThat(noToken.failedToConnect()).isTrue();

		Connection badToken = connect("Bearer not-a-jwt");
		assertThat(badToken.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
		assertThat(badToken.failedToConnect()).isTrue();
	}

	@Test
	@DisplayName("비참여자의 방 구독은 ERROR(CHAT_NOT_PARTICIPANT), 없는 방은 CHAT_ROOM_NOT_FOUND로 거부한다")
	void subscribeRequiresParticipant() throws Exception {
		Connection strangerConnection = connect(bearerToken(stranger.getId()));
		strangerConnection.subscribeAsync("/topic/chat-rooms." + roomId, ChatMessageResponse.class,
			new LinkedBlockingQueue<>());
		assertThat(strangerConnection.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
			.isEqualTo("CHAT_NOT_PARTICIPANT");

		Connection renterConnection = connect(bearerToken(renter.getId()));
		renterConnection.subscribeAsync("/topic/chat-rooms.999999", ChatMessageResponse.class,
			new LinkedBlockingQueue<>());
		assertThat(renterConnection.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
			.isEqualTo("CHAT_ROOM_NOT_FOUND");
	}

	@Test
	@DisplayName("정해지지 않은 목적지 구독과 브로커 목적지로 바로 SEND는 ERROR(AUTH_FORBIDDEN)로 거부한다")
	void forbiddenDestinations() throws Exception {
		Connection subscriber = connect(bearerToken(renter.getId()));
		subscriber.subscribeAsync("/topic/chat-rooms.*", Object.class, new LinkedBlockingQueue<>());
		assertThat(subscriber.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_FORBIDDEN");

		Connection sender = connect(bearerToken(renter.getId()));
		sender.send("/topic/chat-rooms." + roomId, textMessage("우회", "tmp-0"));
		assertThat(sender.errors().poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo("AUTH_FORBIDDEN");
		assertThat(messageCount()).isZero();
	}

	@Test
	@DisplayName("참여자가 보낸 메시지는 저장된 뒤 양쪽 구독자에게 id·sentAt과 함께 가고, 상대에게 목록 갱신 이벤트가 간다")
	void sendAndReceive() throws Exception {
		Connection ownerConnection = connect(bearerToken(owner.getId()));
		Connection renterConnection = connect(bearerToken(renter.getId()));
		BlockingQueue<ChatMessageResponse> ownerRoom = ownerConnection.subscribe("/topic/chat-rooms." + roomId,
			ChatMessageResponse.class);
		BlockingQueue<ChatRoomUpdateResponse> ownerUpdates = ownerConnection.subscribe("/user/queue/chat-rooms",
			ChatRoomUpdateResponse.class);
		BlockingQueue<ChatMessageResponse> renterRoom = renterConnection.subscribe("/topic/chat-rooms." + roomId,
			ChatMessageResponse.class);

		renterConnection.send("/pub/chat-rooms/" + roomId, textMessage("내일 빌릴 수 있을까요?", "tmp-1"));

		ChatMessageResponse received = ownerRoom.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(received).isNotNull();
		assertThat(received.messageId()).isNotNull();
		assertThat(received.roomId()).isEqualTo(roomId);
		assertThat(received.senderId()).isEqualTo(renter.getId());
		assertThat(received.type()).isEqualTo(ChatMessageType.TEXT);
		assertThat(received.content()).isEqualTo("내일 빌릴 수 있을까요?");
		assertThat(received.sentAt()).isNotNull();
		assertThat(received.clientMessageId()).isEqualTo("tmp-1");

		ChatMessageResponse echoed = renterRoom.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(echoed).isNotNull();
		assertThat(echoed.messageId()).isEqualTo(received.messageId());

		ChatRoomUpdateResponse update = ownerUpdates.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(update).isNotNull();
		assertThat(update.roomId()).isEqualTo(roomId);
		assertThat(update.lastMessageId()).isEqualTo(received.messageId());
		assertThat(update.lastMessagePreview()).isEqualTo("내일 빌릴 수 있을까요?");

		Map<String, Object> room = jdbcTemplate.queryForMap(
			"SELECT last_message_preview, last_message_at FROM chat_rooms WHERE id = ?", roomId);
		assertThat(room.get("last_message_preview")).isEqualTo("내일 빌릴 수 있을까요?");
		assertThat(room.get("last_message_at")).isNotNull();
		assertThat(messageCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("비참여자의 SEND는 저장하지 않고 보낸 세션의 /user/queue/errors로 CHAT_NOT_PARTICIPANT를 돌려준다(연결 유지)")
	void sendRequiresParticipant() throws Exception {
		Connection strangerConnection = connect(bearerToken(stranger.getId()));
		BlockingQueue<ChatErrorResponse> errors = strangerConnection.subscribe("/user/queue/errors",
			ChatErrorResponse.class);

		strangerConnection.send("/pub/chat-rooms/" + roomId, textMessage("끼어들기", "tmp-x"));

		ChatErrorResponse error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(error).isNotNull();
		assertThat(error.clientMessageId()).isEqualTo("tmp-x");
		assertThat(error.error().code()).isEqualTo("CHAT_NOT_PARTICIPANT");
		assertThat(messageCount()).isZero();
		assertThat(strangerConnection.isConnected()).isTrue();
	}

	@Test
	@DisplayName("1,000자를 넘거나 공백만인 메시지, TEXT가 아닌 type은 COMMON_INVALID_INPUT으로 거부한다. 1,000자는 보낼 수 있다")
	void messageValidation() throws Exception {
		Connection renterConnection = connect(bearerToken(renter.getId()));
		BlockingQueue<ChatErrorResponse> errors = renterConnection.subscribe("/user/queue/errors",
			ChatErrorResponse.class);
		BlockingQueue<ChatMessageResponse> room = renterConnection.subscribe("/topic/chat-rooms." + roomId,
			ChatMessageResponse.class);

		renterConnection.send("/pub/chat-rooms/" + roomId, textMessage("가".repeat(1001), "too-long"));
		renterConnection.send("/pub/chat-rooms/" + roomId, textMessage("   ", "blank"));
		renterConnection.send("/pub/chat-rooms/" + roomId,
			Map.of("type", "SYSTEM", "content", "시스템 사칭", "clientMessageId", "system"));

		for (String expected : new String[] {"too-long", "blank", "system"}) {
			ChatErrorResponse error = errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			assertThat(error).isNotNull();
			assertThat(error.clientMessageId()).isEqualTo(expected);
			assertThat(error.error().code()).isEqualTo("COMMON_INVALID_INPUT");
		}

		renterConnection.send("/pub/chat-rooms/" + roomId, textMessage("가".repeat(1000), "max"));
		ChatMessageResponse accepted = room.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(accepted).isNotNull();
		assertThat(accepted.clientMessageId()).isEqualTo("max");
		assertThat(messageCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("알림을 저장하면 받는 회원의 /user/queue/notifications로 실시간 전달된다 (REQ-032)")
	void notificationPush() throws Exception {
		Connection renterConnection = connect(bearerToken(renter.getId()));
		BlockingQueue<NotificationResponse> notifications = renterConnection.subscribe("/user/queue/notifications",
			NotificationResponse.class);

		notificationService.send(renter.getId(), NotificationType.OVERDUE, NotificationTargetType.RENTAL, 10L,
			"OVERDUE:10");

		NotificationResponse pushed = notifications.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(pushed).isNotNull();
		assertThat(pushed.type()).isEqualTo(NotificationType.OVERDUE);
		assertThat(pushed.targetId()).isEqualTo(10L);
	}

	private Connection connect(String authorization) {
		Connection connection = StompTestClient.connect(port, authorization);
		connections.add(connection);
		return connection;
	}

	private Map<String, String> textMessage(String content, String clientMessageId) {
		return Map.of("type", "TEXT", "content", content, "clientMessageId", clientMessageId);
	}

	private long messageCount() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages", Long.class);
	}
}
