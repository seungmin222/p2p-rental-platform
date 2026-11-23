package com.team7.rental.item.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.repository.ItemViewLogRepository;

import lombok.RequiredArgsConstructor;

/** 최근 본 상품 기록 (REQ-035). 상세 조회 트랜잭션 밖에서 {@link ItemDetailService}가 부른다 */
@Service
@RequiredArgsConstructor
public class RecentItemWriter {

	private final ItemViewLogRepository itemViewLogRepository;
	private final PolicyProperties policies;
	private final Clock clock;

	/**
	 * (회원, 물품) 기록을 upsert하고 회원당 최신 recent-items.max건을 넘는 기록을 지운다. 쿼리는 upsert 1 + 정리 대상 조회 1(+ 넘칠 때 삭제
	 * 1)이다. 같은 회원의 동시 조회로 잠깐 max건을 넘어도 다음 기록 때 정리되고, 목록은 읽을 때도 max건으로 자른다.
	 */
	@Transactional
	public void record(Long memberId, Long itemId) {
		itemViewLogRepository.upsert(memberId, itemId, LocalDateTime.now(clock));
		List<Long> beyond = itemViewLogRepository.findIdsBeyond(memberId, policies.recentItems().max());
		if (!beyond.isEmpty()) {
			itemViewLogRepository.deleteAllByIdIn(beyond);
		}
	}
}
