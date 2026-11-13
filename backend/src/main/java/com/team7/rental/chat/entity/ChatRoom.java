package com.team7.rental.chat.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 채팅방 (REQ-026). (물품, 대여자)당 1개(UK item_id+renter_id). 참여자는 대여자와 물품 소유자 둘이다 */
@Getter
@Entity
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long itemId;

	@Column(nullable = false)
	private Long renterId;

	@Column(nullable = false)
	private Long ownerId;

	private LocalDateTime lastMessageAt;

	@Column(length = 1000)
	private String lastMessagePreview;

	public static ChatRoom open(Long itemId, Long renterId, Long ownerId) {
		ChatRoom room = new ChatRoom();
		room.itemId = itemId;
		room.renterId = renterId;
		room.ownerId = ownerId;
		return room;
	}

	public boolean isParticipant(Long memberId) {
		return renterId.equals(memberId) || ownerId.equals(memberId);
	}

	/** 참여자의 상대방 */
	public Long counterpartOf(Long memberId) {
		return renterId.equals(memberId) ? ownerId : renterId;
	}

	/** 목록 정렬·미리보기용 마지막 메시지 기록 (REQ-027) */
	public void recordLastMessage(String content, LocalDateTime sentAt) {
		this.lastMessagePreview = content;
		this.lastMessageAt = sentAt;
	}
}
