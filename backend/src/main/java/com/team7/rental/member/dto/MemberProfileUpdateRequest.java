package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로필 수정 multipart의 request part (REQ-005). 보낸 항목만 바꾼다. 닉네임을 바꾸지 않으면 part를 생략해도 된다.
 */
@ValidMemberText
public record MemberProfileUpdateRequest(
	@Schema(description = "새 닉네임 (선택). 글자 수는 policies.md member.nickname", example = "새닉네임")
	String nickname) {
}
