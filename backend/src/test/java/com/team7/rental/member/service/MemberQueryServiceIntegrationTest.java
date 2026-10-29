package com.team7.rental.member.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.member.entity.Member;
import com.team7.rental.support.IntegrationTestSupport;

/** MemberQueryService.getNicknames (REQ-019 빌린 내역의 닉네임 일괄 조회) */
class MemberQueryServiceIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MemberQueryService memberQueryService;

	@Test
	@DisplayName("빈 목록이면 빈 Map이다")
	void empty() {
		assertThat(memberQueryService.getNicknames(List.of())).isEmpty();
	}

	@Test
	@DisplayName("회원 ID별 닉네임을 돌려주고, 없는 ID는 담지 않는다")
	void nicknames() {
		// given
		Member first = fixture.createMember();
		Member second = fixture.createMember();

		// when
		Map<Long, String> nicknames = memberQueryService.getNicknames(
			List.of(first.getId(), second.getId(), second.getId() + 999));

		// then
		assertThat(nicknames).containsExactlyInAnyOrderEntriesOf(
			Map.of(first.getId(), first.getNickname(), second.getId(), second.getNickname()));
	}
}
