package com.team7.rental.member.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.member.client.RegionLookupClient;
import com.team7.rental.member.dto.MemberRegionAddRequest;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.dto.RegionCoordRequest;
import com.team7.rental.member.entity.MemberRegion;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 내 동네 추가·인증·대표 설정·삭제 (REQ-004).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberRegionService {
	private final MemberRepository memberRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final RegionRepository regionRepository;
	private final MemberRegionMapper mapper;
	private final MemberRegionQueryService queryService;
	private final RegionLookupClient regionLookupClient;
	private final MemberRegionVerificationWriter verificationWriter;

	@Value("${app.policy.region.max-per-member}")
	private int maxPerMember;

	/** 첫 동네면 자동으로 대표 동네 */
	@Transactional
	public MemberRegionResponse add(Long memberId, MemberRegionAddRequest request) {
		lockMember(memberId);
		var region = regionRepository.findById(request.regionId())
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("regionId", "존재하지 않는 행정동입니다."))));
		var existing = memberRegionRepository.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId);
		if (existing.stream().anyMatch(value -> value.getRegionId().equals(region.getId()))) {
			throw new BusinessException(ErrorCode.REGION_ALREADY_ADDED);
		}
		if (existing.size() >= maxPerMember) {
			throw new BusinessException(ErrorCode.REGION_LIMIT_EXCEEDED);
		}
		var saved = memberRegionRepository
			.saveAndFlush(MemberRegion.create(memberId, region.getId(), existing.isEmpty()));
		return mapper.toResponse(saved, region);
	}

	/** 좌표의 행정동이 이 동네면 verifiedAt = now. 좌표는 저장하지 않는다 */
	// 외부 호출(카카오 로컬 좌표 변환) 후 별도 트랜잭션에서 저장한다. 클래스의 readOnly 트랜잭션이 걸리지 않게 NOT_SUPPORTED
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MemberRegionResponse verify(Long memberId, Long memberRegionId, RegionCoordRequest coord) {
		owned(memberId, memberRegionId);
		String code;
		try {
			code = regionLookupClient.findAdministrativeCode(coord);
		} catch (BusinessException e) {
			// 좌표에 행정동(H)이 없는 경우만 인증 불일치로 처리한다.
			// 소유권 조회와 DB 저장의 404는 이 catch 바깥에 둔다.
			if (e.getErrorCode() == ErrorCode.COMMON_NOT_FOUND) {
				throw new BusinessException(ErrorCode.REGION_VERIFY_MISMATCH);
			}
			throw e;
		}
		return verificationWriter.verify(memberId, memberRegionId, code);
	}

	/** @return 바뀐 뒤의 내 동네 목록 (대표 동네 먼저) */
	@Transactional
	public List<MemberRegionResponse> changePrimary(Long memberId, Long memberRegionId) {
		lockMember(memberId);
		owned(memberId, memberRegionId);
		var regions = memberRegionRepository.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId);
		regions.forEach(region -> region.changePrimary(region.getId().equals(memberRegionId)));
		memberRegionRepository.flush();
		return queryService.getMyRegions(memberId);
	}

	/** 대표 동네를 지우면 남은 동네가 대표가 된다 */
	@Transactional
	public void delete(Long memberId, Long memberRegionId) {
		lockMember(memberId);
		MemberRegion target = owned(memberId, memberRegionId);
		memberRegionRepository.delete(target);
		memberRegionRepository.flush();
		if (target.isPrimary()) {
			var remaining = memberRegionRepository.findByMemberIdOrderByIsPrimaryDescIdAsc(memberId);
			if (!remaining.isEmpty()) {
				remaining.getFirst().changePrimary(true);
			}
		}
	}

	private void lockMember(Long memberId) {
		memberRepository.findForUpdate(memberId)
			.filter(member -> member.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
	}

	private MemberRegion owned(Long memberId, Long memberRegionId) {
		return memberRegionRepository.findByIdAndMemberId(memberRegionId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}
}
