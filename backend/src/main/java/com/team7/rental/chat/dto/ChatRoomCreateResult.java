package com.team7.rental.chat.dto;

/** 서비스 → 컨트롤러: 응답 본문과 새로 만들었는지(201/200 구분) */
public record ChatRoomCreateResult(ChatRoomCreateResponse room, boolean created) {
}
