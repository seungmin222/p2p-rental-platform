package com.team7.rental.payment.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.payment.dto.SettlementListResponse;
import com.team7.rental.payment.dto.SettlementResponse;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.dto.RentalSummary;
import com.team7.rental.rental.service.RentalSummaryReader;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SettlementQueryService {

	private final SettlementRepository settlementRepository;
	private final RentalSummaryReader rentalSummaryReader;
	private final PolicyProperties policyProperties;

	/**
	 * 내 정산 내역 (REQ-039). 본인이 제공자인 정산만. 대여·물품 정보는 페이지 단위로 한 번에 읽는다.
	 */
	public SettlementListResponse getMySettlements(Long memberId, int page) {
		Page<Settlement> settlements = settlementRepository.findByOwnerIdOrderByIdDesc(memberId,
			PageRequest.of(page, policyProperties.settlement().page()));
		Set<Long> rentalIds = settlements.stream().map(Settlement::getRentalId).collect(Collectors.toSet());
		Map<Long, RentalSummary> rentals = rentalSummaryReader.getSummaries(rentalIds);
		return new SettlementListResponse(
			settlementRepository.sumPayoutAmount(memberId, SettlementStatus.SCHEDULED),
			settlementRepository.sumPayoutAmount(memberId, SettlementStatus.PAID),
			PageResponse.of(settlements,
				settlement -> SettlementResponse.of(settlement, rentalOf(rentals, settlement))));
	}

	private RentalSummary rentalOf(Map<Long, RentalSummary> rentals, Settlement settlement) {
		RentalSummary rental = rentals.get(settlement.getRentalId());
		if (rental == null) {
			throw new IllegalStateException("정산이 참조하는 대여가 없습니다: settlementId=" + settlement.getId());
		}
		return rental;
	}
}
