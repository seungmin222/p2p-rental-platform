package com.team7.rental.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅방 정보 (REQ-026·028, SCR-10 상단 바) */
public record ChatRoomDetailResponse(
	@Schema(description = "채팅방 ID", example = "3")
	Long roomId,

	@Schema(description = "물품 소유자(제공자) 회원 ID", example = "2")
	Long ownerId,

	@Schema(description = "대여 희망자(방을 만든 회원) ID", example = "5")
	Long renterId,

	ChatCounterpartResponse counterpart,

	ChatItemResponse item,

	@Schema(description = "이 (물품, 대여자) 조합의 가장 최근 대여 건(상태 무관, CANCELED 포함). 대여한 적이 없으면 null", nullable = true)
	ChatLatestRentalResponse latestRental,

	@Schema(description = "상대의 읽음 위치(메시지 ID). 이 값 이하인 내 메시지는 '읽음'으로 표시한다. 아직 읽지 않았으면 null", nullable = true)
	Long counterpartLastReadMessageId) {
}
