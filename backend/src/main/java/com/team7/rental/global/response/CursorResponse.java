package com.team7.rental.global.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커서 기반 목록 응답 (docs/architecture.md 페이지네이션). 피드·채팅 내역·알림처럼 무한 스크롤 목록이 쓴다.
 * 다음 요청은 nextCursor를 그대로 cursor 파라미터로 보낸다. 커서 형식은 API마다 다르고 클라이언트는 해석하지 않는다.
 */
public record CursorResponse<T>(
	@Schema(description = "이번에 가져온 항목")
	List<T> items,

	@Schema(description = "다음 요청의 cursor. 다음이 없으면 null", example = "MjAyNi0xMC0yOVQxMDowMDowMHwxMjM")
	String nextCursor,

	@Schema(description = "다음 항목이 있으면 true")
	boolean hasNext) {
}
