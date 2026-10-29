package com.team7.rental.rental.repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.team7.rental.rental.entity.RentalStatus;

/** 대여 동적 목록 조회 (QueryDSL, 조회 전용). 구현은 RentalSearchRepositoryImpl */
public interface RentalSearchRepository {

	/**
	 * 빌린 내역(REQ-019): renterId가 빌린 대여 중 statuses에 속하는 것. OVERDUE 먼저, 그다음 startDate DESC, id DESC.
	 * 전체 수(count)는 필요할 때만 조회한다.
	 */
	Page<RentalHistoryRow> findRenterHistory(Long renterId, Collection<RentalStatus> statuses, Pageable pageable);
}
