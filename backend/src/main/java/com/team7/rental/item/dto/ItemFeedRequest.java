package com.team7.rental.item.dto;

import com.team7.rental.member.entity.RegionRange;

import io.swagger.v3.oas.annotations.media.Schema;

/** 동네 피드 쿼리 파라미터 (REQ-009). 페이지 크기는 policies.md feed.page로 고정이다 */
public record ItemFeedRequest(
	@Schema(description = "기준 행정동 ID. 생략하면 로그인 회원의 대표 동네(비회원·대표 동네 없음이면 400)", example = "1234")
	Long regionId,

	@Schema(description = "동네 범위(거리는 policies.md region.range). 동네 범위 설정(REQ-042) 전에는 무엇을 보내도 NORMAL로 처리한다")
	RegionRange range,

	@Schema(description = "카테고리 ID. 생략하면 전체", example = "1")
	Long categoryId,

	@Schema(description = "이전 응답의 nextCursor. 첫 페이지는 생략", example = "MjAyNi0xMC0yOVQxMDowMDowMHwxMjM")
	String cursor) {
}
