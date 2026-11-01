package com.team7.rental.member.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.entity.MemberRegion;
import com.team7.rental.member.entity.Region;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberRegionMapper {
	private final PolicyProperties policies;
	private final Clock clock;

	public MemberRegionResponse toResponse(MemberRegion memberRegion, Region region) {
		int days = policies.region().verifyValidDays();
		var verifiedAt = memberRegion.getVerifiedAt();
		return new MemberRegionResponse(memberRegion.getId(), region.getId(), region.getName(),
			(region.getSido() + " " + region.getSigungu() + " " + region.getName()).trim(),
			memberRegion.isPrimary(), memberRegion.isVerified(LocalDateTime.now(clock), days),
			verifiedAt, verifiedAt == null ? null : verifiedAt.plusDays(days));
	}
}
