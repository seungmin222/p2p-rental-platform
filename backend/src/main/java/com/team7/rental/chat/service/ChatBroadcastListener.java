package com.team7.rental.chat.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.chat.dto.ChatRoomUpdateResponse;
import com.team7.rental.chat.event.ChatMessageSentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 메시지 저장이 커밋된 뒤 전파한다 (REQ-027). 롤백된 메시지는 나가지 않는다.
 * 실패는 로그만 남기고 재전송하지 않는다(DB가 기준, 재연결 시 REST로 보충).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {

	private final SimpMessagingTemplate messagingTemplate;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessageSent(ChatMessageSentEvent event) {
		var message = event.message();
		try {
			messagingTemplate.convertAndSend(ChatDestinations.roomTopic(message.roomId()), message);
		} catch (RuntimeException e) {
			log.warn("Chat broadcast failed (saved): roomId={}, messageId={}, error={}", message.roomId(),
				message.messageId(), e.toString());
		}
		try {
			messagingTemplate.convertAndSendToUser(event.recipientId().toString(), ChatDestinations.ROOM_UPDATES_QUEUE,
				ChatRoomUpdateResponse.of(message));
		} catch (RuntimeException e) {
			log.warn("Chat room update push failed: roomId={}, recipientId={}, error={}", message.roomId(),
				event.recipientId(), e.toString());
		}
	}
}
