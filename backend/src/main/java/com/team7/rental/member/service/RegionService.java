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
