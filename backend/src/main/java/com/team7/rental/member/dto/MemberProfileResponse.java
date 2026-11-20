package com.team7.rental.member.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 프로필 (REQ-021, SCR-13). 이메일 등 개인정보는 담지 않는다. 탈퇴 회원이면 memberId·isWithdrawn만 채우고 나머지는 null이다("탈퇴한 사용자" 표시는
 * 프론트가 한다). 신뢰 점수·칭찬 항목별 횟수는 REQ-054에서 붙인다.
 */
public record MemberProfileResponse(
	@Schema(description = "회원 ID", example = "1")
	Long memberId,

	@Schema(description = "닉네임. 탈퇴 회원이면 null", example = "동네주민")
	String nickname,

	@Schema(description = "프로필 이미지 URL. 없거나 탈퇴 회원이면 null", example = "/files/profiles/1/a1b2c3.jpg")
	String profileImageUrl,

	@Schema(description = "대표 동네 이름. 동네가 없거나 탈퇴 회원이면 null", example = "송도1동")
	String regionName,

	@Schema(description = "가입 시각. 탈퇴 회원이면 null", example = "2026-10-01T12:00:00")
	LocalDateTime joinedAt,

	@Schema(description = "거래 횟수 = 대여자·제공자로 거래 완료(COMPLETED)된 대여 수. 탈퇴 회원이면 null", example = "12")
	Long completedRentalCount,

	@Schema(description = "탈퇴(WITHDRAWN) 회원이면 true → \"탈퇴한 사용자\" 표시, 상세 비공개")
	boolean isWithdrawn) {

	public static MemberProfileResponse withdrawn(Long memberId) {
		return new MemberProfileResponse(memberId, null, null, null, null, null, true);
	}
}
