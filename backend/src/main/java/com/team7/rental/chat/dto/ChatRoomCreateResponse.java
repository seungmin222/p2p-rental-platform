package com.team7.rental.chat.dto;

import com.team7.rental.chat.entity.ChatRoom;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅방 생성 결과 (REQ-026). 새로 만들었으면 201, 이미 있던 방이면 200 */
public record ChatRoomCreateResponse(
	@Schema(description = "채팅방 ID. STOMP 구독 /topic/chat-rooms.{roomId}, 전송 /pub/chat-rooms/{roomId}", example = "3")
	Long roomId,

	@Schema(description = "물품 ID", example = "7")
	Long itemId,

	@Schema(description = "물품 소유자(제공자) 회원 ID", example = "2")
	Long ownerId,

	@Schema(description = "대여 희망자(방을 만든 회원) ID", example = "5")
	Long renterId) {

	public static ChatRoomCreateResponse of(ChatRoom room) {
		return new ChatRoomCreateResponse(room.getId(), room.getItemId(), room.getOwnerId(), room.getRenterId());
	}
}
