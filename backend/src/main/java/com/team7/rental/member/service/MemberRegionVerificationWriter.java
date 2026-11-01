package com.team7.rental.member.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/** 외부 좌표 조회가 끝난 뒤 짧은 트랜잭션에서 소유권을 다시 확인하고 인증한다. */
@Service
@RequiredArgsConstructor
public class MemberRegionVerificationWriter {
	private final MemberRepository memberRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final RegionRepository regionRepository;
	private final MemberRegionMapper mapper;
	private final Clock clock;

	@Transactional
	public MemberRegionResponse verify(Long memberId, Long memberRegionId, String administrativeCode) {
		memberRepository.findForUpdate(memberId)
			.filter(member -> member.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		var target = memberRegionRepository.findByIdAndMemberId(memberRegionId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		var region = regionRepository.findById(target.getRegionId())
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		if (!region.getCode().equals(administrativeCode)) {
			throw new BusinessException(ErrorCode.REGION_VERIFY_MISMATCH);
		}
		target.verify(LocalDateTime.now(clock));
		return mapper.toResponse(target, region);
	}
}
