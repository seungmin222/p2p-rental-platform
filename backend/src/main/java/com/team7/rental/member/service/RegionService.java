package com.team7.rental.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.member.dto.RegionCoordRequest;
import com.team7.rental.member.dto.RegionResponse;

import lombok.RequiredArgsConstructor;

/**
 * 행정동 마스터 조회 (REQ-004). 미구현: 응답 형태만 확정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RegionService {

	/** 이름 LIKE 검색 */
	public List<RegionResponse> search(String keyword) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}

	/**
	 * 카카오 로컬 좌표→행정구역 변환의 행정동(region_type = "H") 코드로 찾는다.
	 *
	 * @throws com.team7.rental.global.error.BusinessException COMMON_NOT_FOUND 해당 행정동이 없을 때
	 */
	public RegionResponse getByCoord(RegionCoordRequest coord) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}
}
