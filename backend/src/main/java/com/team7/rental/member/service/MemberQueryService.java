package com.team7.rental.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.member.entity.Member;
import com.team7.rental.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 다른 도메인이 회원 정보를 읽는 창구. 지금은 대여 상세(REQ-016)의 상대 닉네임만 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberQueryService {

	private final MemberRepository memberRepository;

	/**
	 * @throws IllegalStateException 회원이 없을 때. 호출하는 쪽이 FK로 참조한 회원이라 데이터 오류다
	 */
	public String getNickname(Long memberId) {
		return memberRepository.findById(memberId)
			.map(Member::getNickname)
			.orElseThrow(() -> new IllegalStateException("회원이 없습니다: " + memberId));
	}
}
