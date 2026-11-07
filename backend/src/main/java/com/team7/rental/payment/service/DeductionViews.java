package com.team7.rental.payment.service;

import java.util.List;

import com.team7.rental.payment.entity.DeductionProposal;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.rental.dto.RentalDepositInfo;

/** 결제·차감 제안 → rental 쪽 보증금 정보 변환 (RentalDepositInfo는 결제 Enum을 코드 문자열로 담는다) */
final class DeductionViews {

	private DeductionViews() {}

	static RentalDepositInfo depositInfoOf(Payment payment, DeductionProposal latest, int renterResponseHours) {
		RentalDepositInfo.Deduction deduction = payment.getDamageGrade() == null ? null
			: new RentalDepositInfo.Deduction(payment.getDamageGrade().name(), payment.getDeductionAmount(),
				payment.getDeductionReason());
		return new RentalDepositInfo(payment.getDepositStatus().name(), payment.getRefundedAmount(),
			payment.getRefundPendingAmount() != null, payment.getHeldAt(), deduction,
			latest == null ? null : proposalOf(latest, renterResponseHours));
	}

	static RentalDepositInfo.Proposal proposalOf(DeductionProposal proposal, int renterResponseHours) {
		return new RentalDepositInfo.Proposal(proposal.getId(), proposal.getStatus().name(),
			proposal.getGrade().name(), proposal.getAmount(), proposal.getReason(), proposal.getEvidenceUrls(),
			proposal.getReceiptUrls() == null ? List.of() : proposal.getReceiptUrls(), proposal.getCreatedAt(),
			proposal.responseDeadline(renterResponseHours), proposal.getRespondedAt());
	}
}
