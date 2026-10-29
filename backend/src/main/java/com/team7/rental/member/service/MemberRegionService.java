package com.team7.rental.member.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.member.dto.MemberRegionAddRequest;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.dto.RegionCoordRequest;

import lombok.RequiredArgsConstructor;

/**
 * 내 동네 추가·인증·대표 설정·삭제 (REQ-004). 미구현: 응답 형태만 확정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberRegionService {

	/** 첫 동네면 자동으로 대표 동네 */
	@Transactional
	public MemberRegionResponse add(Long memberId, MemberRegionAddRequest request) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}

	/** 좌표의 행정동이 이 동네면 verifiedAt = now. 좌표는 저장하지 않는다 */
	// 외부 호출(카카오 로컬 좌표 변환) 후 별도 트랜잭션에서 저장한다. 클래스의 readOnly 트랜잭션이 걸리지 않게 NOT_SUPPORTED
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MemberRegionResponse verify(Long memberId, Long memberRegionId, RegionCoordRequest coord) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}

	/** @return 바뀐 뒤의 내 동네 목록 (대표 동네 먼저) */
	@Transactional
	public List<MemberRegionResponse> changePrimary(Long memberId, Long memberRegionId) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}

	/** 대표 동네를 지우면 남은 동네가 대표가 된다 */
	@Transactional
	public void delete(Long memberId, Long memberRegionId) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}
}
