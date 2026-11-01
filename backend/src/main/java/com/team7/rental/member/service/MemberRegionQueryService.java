package com.team7.rental.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.entity.Region;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.RegionRepository;

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
	private final RegionRepository regionRepository;
	private final MemberRegionMapper mapper;

	/** 대표 동네가 있고 인증이 유효한지 (region.verify-valid-days) */
	public boolean isPrimaryRegionVerified(Long memberId) {
		LocalDateTime now = LocalDateTime.now(clock);
		return memberRegionRepository.findFirstByMemberIdAndIsPrimaryTrue(memberId)
			.map(region -> region.isVerified(now, policyProperties.region().verifyValidDays()))
			.orElse(false);
	}

	/** 내 동네 목록 (REQ-004). 대표 동네 먼저 */
	public List<MemberRegionResponse> getMyRegions(Long memberId) {
		var members = memberRegionRepository.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId);
		if (members.isEmpty()) {
			return List.of();
		}
		var regions = regionRepository.findAllById(members.stream().map(value -> value.getRegionId()).toList())
			.stream().collect(Collectors.toMap(Region::getId, Function.identity()));
		return members.stream().map(value -> {
			Region region = regions.get(value.getRegionId());
			if (region == null) {
				throw new IllegalStateException("회원 동네 참조 오류");
			}
			return mapper.toResponse(value, region);
		}).toList();
	}
}
