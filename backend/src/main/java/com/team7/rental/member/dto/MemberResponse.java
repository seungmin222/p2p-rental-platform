package com.team7.rental.member.dto;

import java.time.LocalDateTime;

import com.team7.rental.member.entity.MemberRole;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 정보 (REQ-005, SCR-12·13). 프로필 수정 응답도 같다 */
public record MemberResponse(
	@Schema(description = "회원 ID", example = "1")
	Long memberId,

	@Schema(description = "이메일", example = "user@example.com")
	String email,

	@Schema(description = "닉네임", example = "동네주민")
	String nickname,

	@Schema(description = "프로필 이미지 URL. 없으면 null", example = "/files/profiles/1/a1b2c3.jpg")
	String profileImageUrl,

	@Schema(description = "권한")
	MemberRole role,

	@Schema(description = "대표 동네. 동네를 아직 추가하지 않았으면 null")
	PrimaryRegion primaryRegion,

	@Schema(description = "가입 시각", example = "2026-10-01T12:00:00")
	LocalDateTime createdAt) {

	/** 대표 동네 요약. 인증 유효 여부는 verifiedAt + policies.md region.verify-valid-days 기준 */
	public record PrimaryRegion(
		@Schema(description = "내 동네 ID (/api/members/me/regions/{id}의 id)", example = "3")
		Long memberRegionId,

		@Schema(description = "행정동 ID", example = "1234")
		Long regionId,

		@Schema(description = "행정동 이름", example = "송도1동")
		String regionName,

		@Schema(description = "인증이 유효하면 true (인증한 적 없거나 만료면 false)")
		boolean isVerified,

		@Schema(description = "마지막 인증 시각. 인증한 적 없으면 null", example = "2026-10-20T09:30:00")
		LocalDateTime verifiedAt,

		@Schema(description = "인증 만료 시각 = verifiedAt + region.verify-valid-days. 인증한 적 없으면 null", example = "2026-11-19T09:30:00")
		LocalDateTime verifyExpiresAt) {
	}
}
