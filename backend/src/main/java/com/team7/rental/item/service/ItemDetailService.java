package com.team7.rental.item.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.item.dto.ItemDetailResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 물품 상세 (REQ-011) + 최근 본 상품 기록 (REQ-035). 트랜잭션을 두지 않는다: 읽기 전용 상세 트랜잭션이 끝난 뒤 기록 트랜잭션을 따로 연다. 상세 안에서
 * REQUIRES_NEW로 기록하면 요청 하나가 커넥션 두 개를 잡아 부하 시 풀이 고갈될 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemDetailService {

	private final ItemQueryService itemQueryService;
	private final RecentItemWriter recentItemWriter;

	/**
	 * 로그인 회원이 남의 물품을 보면 최근 본 상품에 기록한다. 본인 물품(숨김 포함)은 수정·관리 목적이라 기록하지 않는다. 기록 실패는 상세 응답을 막지 않는다.
	 *
	 * @param memberId 비회원이면 null
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public ItemDetailResponse getDetail(Long itemId, Long memberId, String remoteAddress) {
		ItemDetailResponse detail = itemQueryService.getDetail(itemId, memberId, remoteAddress);
		if (memberId != null && !detail.isOwner()) {
			try {
				recentItemWriter.record(memberId, itemId);
			} catch (DataAccessException failure) {
				// 부가 기록 장애가 상세 조회를 막지 않게 한다. 회원 정보는 로그에 남기지 않는다.
				log.warn("최근 본 상품 기록 실패: itemId={}, cause={}", itemId, failure.getClass().getSimpleName());
			}
		}
		return detail;
	}
}
