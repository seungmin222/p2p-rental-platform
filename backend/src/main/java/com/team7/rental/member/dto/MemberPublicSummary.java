package com.team7.rental.member.dto;

/**
 * 다른 도메인에 전달하는 공개 회원 요약. 이메일·비밀번호는 포함하지 않는다.
 *
 * @param regionName 대표 동네 이름. 없으면 null
 * @param isWithdrawn 탈퇴(WITHDRAWN) 회원이면 true. "탈퇴한 사용자" 표시 여부는 받는 쪽이 정한다
 */
public record MemberPublicSummary(Long memberId, String nickname, String profileImageUrl, String regionName,
	boolean isWithdrawn) {
}
