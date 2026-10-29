package com.team7.rental.member.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 조회. 내 정보(REQ-005)와, 다른 도메인이 회원 정보를 읽는 창구(대여 상세 REQ-016·빌린 내역 REQ-019의 닉네임)를 겸한다.
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

	/**
	 * {@link #getNickname}의 일괄 조회판(목록 화면용). 쿼리 1개.
	 *
	 * @return 회원 ID → 닉네임. 없는 ID는 담지 않는다(없는 경우의 처리는 호출하는 쪽이 정한다)
	 */
	public Map<Long, String> getNicknames(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		return memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, Member::getNickname));
	}

	/** 내 정보 (REQ-005). 대표 동네 요약 포함 */
	public MemberResponse getMe(Long memberId) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}
}
