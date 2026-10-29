package com.team7.rental.member.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 동네 (REQ-004). 인증 좌표는 저장하지 않으므로 응답에도 없다 */
public record MemberRegionResponse(
	@Schema(description = "내 동네 ID (경로의 {memberRegionId})", example = "3")
	Long memberRegionId,

	@Schema(description = "행정동 ID", example = "1234")
	Long regionId,

	@Schema(description = "행정동 이름", example = "송도1동")
	String regionName,

	@Schema(description = "시·도 시·군·구 행정동 전체 이름", example = "인천광역시 연수구 송도1동")
	String fullName,

	@Schema(description = "대표 동네면 true (피드·검색 기준, 물품 등록·예약 인증 기준)")
	boolean isPrimary,

	@Schema(description = "인증이 유효하면 true (인증한 적 없거나 만료면 false)")
	boolean isVerified,

	@Schema(description = "마지막 인증 시각. 인증한 적 없으면 null", example = "2026-10-20T09:30:00")
	LocalDateTime verifiedAt,

	@Schema(description = "인증 만료 시각 = verifiedAt + policies.md region.verify-valid-days. 인증한 적 없으면 null", example = "2026-11-19T09:30:00")
	LocalDateTime verifyExpiresAt) {
}
