package com.team7.rental.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.repository.MemberRegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 내 동네 조회(REQ-004)와, 다른 도메인이 회원 동네 정보를 읽는 창구(예약 REQ-013의 대표 동네 인증 확인)를 겸한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberRegionQueryService {

	private final MemberRegionRepository memberRegionRepository;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/** 대표 동네가 있고 인증이 유효한지 (region.verify-valid-days) */
	public boolean isPrimaryRegionVerified(Long memberId) {
		LocalDateTime now = LocalDateTime.now(clock);
		return memberRegionRepository.findFirstByMemberIdAndIsPrimaryTrue(memberId)
			.map(region -> region.isVerified(now, policyProperties.region().verifyValidDays()))
			.orElse(false);
	}

	/** 내 동네 목록 (REQ-004). 대표 동네 먼저 */
	public List<MemberRegionResponse> getMyRegions(Long memberId) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}
}
