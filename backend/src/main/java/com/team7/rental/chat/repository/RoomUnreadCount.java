package com.team7.rental.chat.repository;

/** 방별 안 읽은 메시지 수 (REQ-029 목록 집계 결과) */
public record RoomUnreadCount(Long roomId, long count) {
}
