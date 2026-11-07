package com.team7.rental.payment.entity;

/** 차감 제안 상태 (state-machines.md §5) */
public enum DeductionProposalStatus {
	PROPOSED, ACCEPTED, REJECTED, AUTO_ACCEPTED, ADMIN_DECIDED
}
