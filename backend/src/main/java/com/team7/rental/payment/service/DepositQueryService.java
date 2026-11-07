package com.team7.rental.payment.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.DeductionProposalRepository;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.dto.RentalDepositInfo;
import com.team7.rental.rental.service.RentalDepositQueryPort;

import lombok.RequiredArgsConstructor;

/** 대여 상세의 보증금 정보 (REQ-025·049). 승인된 대여 결제(보증금 상태가 있는 행) 중 최신 것을 본다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DepositQueryService implements RentalDepositQueryPort {

	private final PaymentRepository paymentRepository;
	private final DeductionProposalRepository deductionProposalRepository;
	private final PolicyProperties policyProperties;

	@Override
	public Optional<RentalDepositInfo> findDepositInfo(Long rentalId) {
		return paymentRepository.findAllByRentalIdAndTypeOrderByIdDesc(rentalId, PaymentType.RENTAL).stream()
			.filter(payment -> payment.getDepositStatus() != null)
			.findFirst()
			.map(payment -> depositInfoOf(payment));
	}

	private RentalDepositInfo depositInfoOf(Payment payment) {
		return DeductionViews.depositInfoOf(payment,
			deductionProposalRepository.findFirstByRentalIdOrderByIdDesc(payment.getRentalId()).orElse(null),
			policyProperties.deduction().renterResponseHours());
	}
}
