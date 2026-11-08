package com.team7.rental.payment.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.team7.rental.payment.dto.PaymentHistoryType;

/** 결제·보증금 내역 (REQ-033) QueryDSL 조회 */
public interface PaymentHistoryRepository {

	/**
	 * 대여자 본인의 결제 행. createdAt ∈ [from, toExclusive), 최신 순(createdAt, id 내림차순).
	 *
	 * @param type open-issues #44 임시 해석
	 */
	Page<PaymentHistoryRow> findRenterPayments(Long renterId, PaymentHistoryType type, LocalDateTime from,
		LocalDateTime toExclusive, Pageable pageable);
}
