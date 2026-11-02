package com.team7.rental.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.client.RegionLookupClient;
import com.team7.rental.member.dto.RegionCoordRequest;
import com.team7.rental.member.dto.RegionResponse;
import com.team7.rental.member.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 행정동 마스터 조회 (REQ-004).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegionService {
	private final RegionRepository regionRepository;
	private final RegionLookupClient regionLookupClient;

	public com.team7.rental.member.dto.RegionCenter getCenter(Long regionId) {
		var region = regionRepository.findById(regionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new com.team7.rental.global.response.FieldErrorDetail("regionId", "존재하는 동네를 선택해 주세요."))));
		return new com.team7.rental.member.dto.RegionCenter(region.getId(), region.getCenterLat(),
			region.getCenterLng());
	}

	public java.util.Map<Long, String> getNames(java.util.Collection<Long> regionIds) {
		if (regionIds.isEmpty()) {
			return java.util.Map.of();
		}
		return regionRepository.findAllById(regionIds).stream().collect(java.util.stream.Collectors.toMap(
			com.team7.rental.member.entity.Region::getId, com.team7.rental.member.entity.Region::getName));
	}

	/** 물품 등록 당시 동네 이름 조회. 회원이 대표 동네를 바꿔도 물품 동네는 유지된다. */
	public String getName(Long regionId) {
		return regionRepository.findById(regionId).map(com.team7.rental.member.entity.Region::getName)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
	}

	/** 이름 LIKE 검색 */
	public List<RegionResponse> search(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		return regionRepository.findByNameContainingOrderBySidoAscSigunguAscNameAsc(keyword.strip())
			.stream().map(region -> new RegionResponse(region.getId(), region.getName(),
				(region.getSido() + " " + region.getSigungu() + " " + region.getName()).trim()))
			.toList();
	}

	/**
	 * 카카오 로컬 좌표→행정구역 변환의 행정동(region_type = "H") 코드로 찾는다.
	 *
	 * @throws com.team7.rental.global.error.BusinessException COMMON_NOT_FOUND 해당 행정동이 없을 때
	 */
	// 외부 호출(카카오 로컬 좌표 변환) 후 별도 트랜잭션에서 조회한다. 클래스의 readOnly 트랜잭션이 걸리지 않게 NOT_SUPPORTED
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public RegionResponse getByCoord(RegionCoordRequest coord) {
		String code = regionLookupClient.findAdministrativeCode(coord);
		var region = regionRepository.findByCode(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		return new RegionResponse(region.getId(), region.getName(),
			(region.getSido() + " " + region.getSigungu() + " " + region.getName()).trim());
	}
}
