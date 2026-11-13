package com.team7.rental.chat.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.chat.dto.ChatRoomUpdateResponse;
import com.team7.rental.chat.event.ChatMessageSentEvent;
import com.team7.rental.chat.event.ChatReadEvent;
import com.team7.rental.chat.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 메시지 저장·읽음 갱신이 커밋된 뒤 전파한다 (REQ-027·029). 롤백된 변경은 나가지 않는다.
 * 실패는 로그만 남기고 재전송하지 않는다(DB가 기준, 재연결 시 REST로 보충).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {

	private final SimpMessagingTemplate messagingTemplate;
	private final ChatMessageRepository chatMessageRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMessageSent(ChatMessageSentEvent event) {
		var message = event.message();
		try {
			messagingTemplate.convertAndSend(ChatDestinations.roomTopic(message.roomId()), message);
		} catch (RuntimeException e) {
			log.warn("Chat broadcast failed (saved): roomId={}, messageId={}, error={}", message.roomId(),
				message.messageId(), e.toString());
		}
		Long recipientId = event.recipientId();
		try {
			// 커밋 직후 받는 쪽의 안 읽은 수. 사이에 읽음·새 메시지가 끼어도 다음 이벤트·재조회에서 맞춰진다
			long unreadCount = chatMessageRepository.countUnreadInRoom(message.roomId(), recipientId);
			long totalUnreadCount = chatMessageRepository.countUnread(recipientId);
			messagingTemplate.convertAndSendToUser(recipientId.toString(), ChatDestinations.ROOM_UPDATES_QUEUE,
				ChatRoomUpdateResponse.of(message, unreadCount, totalUnreadCount));
		} catch (RuntimeException e) {
			log.warn("Chat room update push failed: roomId={}, recipientId={}, error={}", message.roomId(), recipientId,
				e.toString());
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRead(ChatReadEvent event) {
		try {
			messagingTemplate.convertAndSendToUser(event.recipientId().toString(), ChatDestinations.READS_QUEUE,
				event.read());
		} catch (RuntimeException e) {
			log.warn("Chat read push failed: roomId={}, recipientId={}, error={}", event.read().roomId(),
				event.recipientId(), e.toString());
		}
	}
}
