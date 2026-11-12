package com.team7.rental.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 채팅방 생성 (REQ-026) */
public record ChatRoomCreateRequest(
	@Schema(description = "대화할 물품 ID. 본인 물품은 불가", example = "7") @NotNull @Positive
	Long itemId) {
}
