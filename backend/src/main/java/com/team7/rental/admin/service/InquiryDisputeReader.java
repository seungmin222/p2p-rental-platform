package com.team7.rental.admin.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.admin.repository.InquiryRepository;
import com.team7.rental.payment.service.SettlementDisputePort;

import lombok.RequiredArgsConstructor;

/** 정산 생성이 묻는 열린 분쟁 여부 (REQ-063). 정산 생성 트랜잭션(대여 행 락 안)에 참여한다 */
@Component
@RequiredArgsConstructor
public class InquiryDisputeReader implements SettlementDisputePort {

	private final InquiryRepository inquiryRepository;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean hasOpenDispute(Long rentalId) {
		return !inquiryRepository.findOpenDisputeIdsForShare(rentalId).isEmpty();
	}
}
