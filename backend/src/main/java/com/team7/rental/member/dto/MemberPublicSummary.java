package com.team7.rental.member.dto;

/** 다른 도메인에 전달하는 공개 회원 요약. 이메일·비밀번호는 포함하지 않는다. */
public record MemberPublicSummary(Long memberId, String nickname, String profileImageUrl, String regionName) {
}
