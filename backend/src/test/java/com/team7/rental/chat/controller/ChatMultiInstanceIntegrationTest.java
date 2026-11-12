package com.team7.rental.chat.controller;

import static com.team7.rental.support.StompTestClient.*;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;

import com.team7.rental.RentalApplication;
import com.team7.rental.chat.dto.ChatMessageResponse;
import com.team7.rental.chat.dto.ChatRoomUpdateResponse;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;
import com.team7.rental.support.StompTestClient;
import com.team7.rental.support.StompTestClient.Connection;

/**
 * 다중 서버 전달 (REQ-027 테스트 포인트): 같은 MySQL·RabbitMQ에 붙은 애플리케이션 2대에서 A는 서버1, B는 서버2에 붙어도
 * 방 메시지와 개인 큐(/user/queue/chat-rooms)가 브로커 릴레이를 거쳐 전달된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatMultiInstanceIntegrationTest extends IntegrationTestSupport {

	/** 서버2. 클래스의 테스트들이 같이 쓰고 끝나면 닫는다 */
	private static ConfigurableApplicationContext secondServer;

	@LocalServerPort
	private int firstPort;

	@Autowired
	private ChatRoomService chatRoomService;

	private Member owner;
	private Member renter;
	private Long roomId;
	private final List<Connection> connections = new ArrayList<>();

	@BeforeEach
	void setUp() {
		if (secondServer == null) {
			// properties()는 기본값이라 application.yml에 덮이므로 명령행 인자로 넘긴다
			List<String> args = new ArrayList<>();
			for (String property : sharedContainerProperties()) {
				args.add("--" + property);
			}
			args.add("--server.port=0");
			secondServer = new SpringApplicationBuilder(RentalApplication.class).profiles("test")
				.run(args.toArray(String[]::new));
		}
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		roomId = chatRoomService.create(renter.getId(), item.getId()).room().roomId();
	}

	@AfterEach
	void disconnectAll() {
		connections.forEach(Connection::disconnect);
	}

	@AfterAll
	static void stopSecondServer() {
		if (secondServer != null) {
			secondServer.close();
			secondServer = null;
		}
	}

	@Test
	@DisplayName("서버1의 대여자와 서버2의 제공자가 서로 메시지를 주고받고, 상대 서버의 개인 큐로 목록 갱신이 간다")
	void deliverAcrossServers() throws Exception {
		Connection renterOnFirst = connect(firstPort, renter);
		Connection ownerOnSecond = connect(secondPort(), owner);
		BlockingQueue<ChatMessageResponse> renterRoom = renterOnFirst.subscribe("/topic/chat-rooms." + roomId,
			ChatMessageResponse.class);
		BlockingQueue<ChatRoomUpdateResponse> renterUpdates = renterOnFirst.subscribe("/user/queue/chat-rooms",
			ChatRoomUpdateResponse.class);
		BlockingQueue<ChatMessageResponse> ownerRoom = ownerOnSecond.subscribe("/topic/chat-rooms." + roomId,
			ChatMessageResponse.class);
		BlockingQueue<ChatRoomUpdateResponse> ownerUpdates = ownerOnSecond.subscribe("/user/queue/chat-rooms",
			ChatRoomUpdateResponse.class);

		renterOnFirst.send("/pub/chat-rooms/" + roomId, Map.of("type", "TEXT", "content", "서버1에서 보냄"));

		ChatMessageResponse onSecond = ownerRoom.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(onSecond).isNotNull();
		assertThat(onSecond.content()).isEqualTo("서버1에서 보냄");
		ChatRoomUpdateResponse ownerUpdate = ownerUpdates.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(ownerUpdate).isNotNull();
		assertThat(ownerUpdate.lastMessageId()).isEqualTo(onSecond.messageId());

		ownerOnSecond.send("/pub/chat-rooms/" + roomId, Map.of("type", "TEXT", "content", "서버2에서 답장"));

		ChatMessageResponse first = renterRoom.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		ChatMessageResponse reply = renterRoom.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(first).isNotNull();
		assertThat(first.content()).isEqualTo("서버1에서 보냄");
		assertThat(reply).isNotNull();
		assertThat(reply.content()).isEqualTo("서버2에서 답장");
		assertThat(reply.messageId()).isGreaterThan(first.messageId());
		ChatRoomUpdateResponse renterUpdate = renterUpdates.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertThat(renterUpdate).isNotNull();
		assertThat(renterUpdate.lastMessageId()).isEqualTo(reply.messageId());
	}

	private int secondPort() {
		return Integer.parseInt(secondServer.getEnvironment().getRequiredProperty("local.server.port"));
	}

	private Connection connect(int port, Member member) {
		Connection connection = StompTestClient.connect(port, bearerToken(member.getId()));
		connections.add(connection);
		return connection;
	}
}
