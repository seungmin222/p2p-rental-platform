package com.team7.rental.chat.dto;

import com.team7.rental.member.dto.MemberPublicSummary;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채팅 상대 (REQ-028). 탈퇴 회원이면 isWithdrawn=true이고 닉네임·사진·동네는 null이다(화면이 "탈퇴한 사용자"로 표시하고 전송을 막는다) */
public record ChatCounterpartResponse(
	@Schema(description = "상대 회원 ID", example = "2")
	Long memberId,

	@Schema(description = "닉네임. 탈퇴 회원이면 null", example = "망원동공구왕", nullable = true)
	String nickname,

	@Schema(description = "프로필 사진 URL. 없거나 탈퇴 회원이면 null", nullable = true)
	String profileImageUrl,

	@Schema(description = "대표 동네 이름. 없거나 탈퇴 회원이면 null", example = "망원동", nullable = true)
	String regionName,

	@Schema(description = "탈퇴한 회원이면 true")
	boolean isWithdrawn) {

	public static ChatCounterpartResponse of(MemberPublicSummary member) {
		if (member.isWithdrawn()) {
			return new ChatCounterpartResponse(member.memberId(), null, null, null, true);
		}
		return new ChatCounterpartResponse(member.memberId(), member.nickname(), member.profileImageUrl(),
			member.regionName(), false);
	}
}
