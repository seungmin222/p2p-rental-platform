package com.team7.rental.chat.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 목적지 (open-issues #14, docs/api.md 채팅). 개인 큐는 {@code /user} 접두사 없이 적는다
 * ({@code SimpMessagingTemplate#convertAndSendToUser}에 그대로 넘기는 값).
 */
public final class ChatDestinations {

	/** 방 메시지 구독. RabbitMQ 토픽이라 구분자가 점이다 */
	public static final String ROOM_TOPIC_PREFIX = "/topic/chat-rooms.";
	/** 상대방 목록 갱신 이벤트 */
	public static final String ROOM_UPDATES_QUEUE = "/queue/chat-rooms";
	/** 상대의 읽음 이벤트 (REQ-029) */
	public static final String READS_QUEUE = "/queue/chat-reads";
	/** STOMP SEND 처리 오류 회신 (보낸 세션에만) */
	public static final String ERRORS_QUEUE = "/queue/errors";
	/** 알림 실시간 전달 (REQ-032) */
	public static final String NOTIFICATIONS_QUEUE = "/queue/notifications";

	private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/chat-rooms\\.(\\d{1,18})$");

	private ChatDestinations() {}

	public static String roomTopic(Long roomId) {
		return ROOM_TOPIC_PREFIX + roomId;
	}

	/** 방 토픽이면 방 ID, 아니면 null */
	public static Long parseRoomTopic(String destination) {
		Matcher matcher = ROOM_TOPIC.matcher(destination);
		return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
	}
}
