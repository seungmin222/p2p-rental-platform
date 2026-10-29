package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 행정동 (REQ-004 동네 검색·좌표 변환) */
public record RegionResponse(
	@Schema(description = "행정동 ID", example = "1234")
	Long regionId,

	@Schema(description = "행정동 이름", example = "송도1동")
	String name,

	@Schema(description = "시·도 시·군·구 행정동 전체 이름", example = "인천광역시 연수구 송도1동")
	String fullName) {
}
