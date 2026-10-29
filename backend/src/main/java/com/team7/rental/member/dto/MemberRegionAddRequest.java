package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 동네 추가 (REQ-004). 인증은 따로(verify) 한다 */
public record MemberRegionAddRequest(
	@Schema(description = "행정동 ID (동네 검색·좌표 변환 결과의 regionId)", example = "1234") @NotNull
	Long regionId) {
}
