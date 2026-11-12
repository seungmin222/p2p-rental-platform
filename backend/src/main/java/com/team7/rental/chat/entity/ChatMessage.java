package com.team7.rental.chat.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅 메시지 (REQ-027). id가 방 안의 순서이자 읽음·커서 기준이다 */
@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long roomId;

	/** SYSTEM 메시지는 null */
	private Long senderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ChatMessageType type;

	@Column(nullable = false, length = 1000)
	private String content;

	@Column(nullable = false)
	private LocalDateTime sentAt;

	public static ChatMessage text(Long roomId, Long senderId, String content, LocalDateTime sentAt) {
		ChatMessage message = new ChatMessage();
		message.roomId = roomId;
		message.senderId = senderId;
		message.type = ChatMessageType.TEXT;
		message.content = content;
		message.sentAt = sentAt;
		return message;
	}
}
