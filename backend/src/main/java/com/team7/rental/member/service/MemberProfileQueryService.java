package com.team7.rental.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.dto.MemberProfileResponse;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.entity.Region;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;
import com.team7.rental.rental.service.RentalCountReader;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 프로필 조회 (REQ-021). 대여 쪽({@link RentalCountReader})을 부르므로 대여 도메인이 의존하는 {@link MemberQueryService}와 클래스를
 * 나눠 빈 순환을 만들지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberProfileQueryService {

	private final MemberRepository memberRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final RegionRepository regionRepository;
	private final RentalCountReader rentalCountReader;

	/**
	 * 비회원도 본다. 쿼리는 회원 1 + 대표 동네 1 + 동네 1 + 거래 횟수 2 = 5개(대표 동네가 없으면 4개, 탈퇴 회원이면 1개).
	 *
	 * @param viewerId 조회하는 회원(비회원이면 null). 차단 관계 처리(REQ-056)를 붙일 자리이고 지금은 쓰지 않는다
	 * @throws BusinessException MEMBER_NOT_FOUND
	 */
	public MemberProfileResponse getProfile(Long memberId, Long viewerId) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.getStatus() == MemberStatus.WITHDRAWN) {
			return MemberProfileResponse.withdrawn(memberId);
		}
		String regionName = memberRegionRepository.findFirstByMemberIdAndIsPrimaryTrue(memberId)
			.flatMap(memberRegion -> regionRepository.findById(memberRegion.getRegionId()))
			.map(Region::getName)
			.orElse(null);
		return new MemberProfileResponse(member.getId(), member.getNickname(), member.getProfileImageUrl(), regionName,
			member.getCreatedAt(), rentalCountReader.countCompleted(memberId), false);
	}
}
