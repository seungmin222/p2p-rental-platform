package com.team7.rental.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 조회. 내 정보(REQ-005)와, 다른 도메인이 회원 정보를 읽는 창구(대여 상세 REQ-016·빌린 내역 REQ-019의 닉네임)를 겸한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberQueryService {

	private final MemberRepository memberRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final RegionRepository regionRepository;
	private final PolicyProperties policyProperties;
	private final Clock clock;

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

	/** 회원 탈퇴 후에도 기존 물품·거래의 공개 요약을 조회할 수 있다. */
	public com.team7.rental.member.dto.MemberPublicSummary getPublicSummary(Long memberId) {
		var member = memberRepository.findById(memberId)
			.orElseThrow(() -> new IllegalStateException("회원 참조 오류"));
		String regionName = memberRegionRepository.findFirstByMemberIdAndIsPrimaryTrue(memberId)
			.flatMap(mr -> regionRepository.findById(mr.getRegionId()))
			.map(com.team7.rental.member.entity.Region::getName).orElse(null);
		return new com.team7.rental.member.dto.MemberPublicSummary(member.getId(), member.getNickname(),
			member.getProfileImageUrl(), regionName, member.getStatus() == MemberStatus.WITHDRAWN);
	}

	/**
	 * {@link #getPublicSummary}의 일괄 조회판(채팅 목록 REQ-028). 쿼리는 회원 수와 상관없이 3개(회원·대표 동네·동네)다.
	 *
	 * @return 회원 ID → 요약. 없는 ID는 담지 않는다(없는 경우의 처리는 호출하는 쪽이 정한다)
	 */
	public Map<Long, com.team7.rental.member.dto.MemberPublicSummary> getPublicSummaries(Collection<Long> memberIds) {
		if (memberIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> primaryRegionIds = memberRegionRepository.findByMemberIdInAndIsPrimaryTrue(memberIds).stream()
			.collect(Collectors.toMap(com.team7.rental.member.entity.MemberRegion::getMemberId,
				com.team7.rental.member.entity.MemberRegion::getRegionId, (first, second) -> first));
		// 대표 동네가 없는 회원은 get(null)이 되므로 null 키 조회가 되는 HashMap을 쓴다
		Map<Long, String> regionNames = primaryRegionIds.isEmpty() ? new java.util.HashMap<>()
			: regionRepository.findAllById(primaryRegionIds.values()).stream()
				.collect(Collectors.toMap(com.team7.rental.member.entity.Region::getId,
					com.team7.rental.member.entity.Region::getName));
		return memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, member -> new com.team7.rental.member.dto.MemberPublicSummary(
				member.getId(), member.getNickname(), member.getProfileImageUrl(),
				regionNames.get(primaryRegionIds.get(member.getId())),
				member.getStatus() == MemberStatus.WITHDRAWN)));
	}

	/** 물품 등록과 대표 동네 변경을 같은 회원 락으로 직렬화한다. 호출자의 쓰기 트랜잭션에 참여한다. */
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
	public Long lockVerifiedPrimaryRegion(Long memberId) {
		memberRepository.findForUpdate(memberId)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		var region = getMe(memberId).primaryRegion();
		if (region == null || !region.isVerified()) {
			throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
		}
		return region.regionId();
	}

	/** 내 정보 (REQ-005). 대표 동네 요약 포함 */
	public MemberResponse getMe(Long memberId) {
		Member member = memberRepository.findById(memberId)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		int days = policyProperties.region().verifyValidDays();
		MemberResponse.PrimaryRegion primary = memberRegionRepository
			.findFirstByMemberIdAndIsPrimaryTrue(memberId)
			.map(mr -> {
				var region = regionRepository.findById(mr.getRegionId())
					.orElseThrow(() -> new IllegalStateException("대표 동네 참조 오류"));
				var expiresAt = mr.getVerifiedAt() == null ? null : mr.getVerifiedAt().plusDays(days);
				return new MemberResponse.PrimaryRegion(mr.getId(), region.getId(), region.getName(),
					mr.isVerified(LocalDateTime.now(clock), days), mr.getVerifiedAt(), expiresAt);
			}).orElse(null);
		return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(),
			member.getProfileImageUrl(), member.getRole(), primary, member.getCreatedAt());
	}
}
