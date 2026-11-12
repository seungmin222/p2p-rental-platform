package com.team7.rental.chat.entity;

import org.springframework.data.domain.Persistable;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅방 참여자별 읽음 위치. 읽음 처리의 유일한 기준이다(erd.md). 방을 만들 때 대여자·소유자 두 행을 넣고,
 * last_read_message_id 갱신은 목록·읽음(REQ-028·029)에서 메서드를 더한다.
 */
@Getter
@Entity
@Table(name = "chat_room_members")
@IdClass(ChatRoomMemberId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember extends BaseTimeEntity implements Persistable<ChatRoomMemberId> {

	@Id
	private Long roomId;

	@Id
	private Long memberId;

	private Long lastReadMessageId;

	public static ChatRoomMember join(Long roomId, Long memberId) {
		ChatRoomMember member = new ChatRoomMember();
		member.roomId = roomId;
		member.memberId = memberId;
		return member;
	}

	@Override
	public ChatRoomMemberId getId() {
		return new ChatRoomMemberId(roomId, memberId);
	}

	/** 키를 직접 넣는 엔티티라 save가 merge(SELECT 후 INSERT)로 가지 않도록, 아직 저장 전인지를 생성 시각으로 판단한다 */
	@Override
	public boolean isNew() {
		return getCreatedAt() == null;
	}
}
