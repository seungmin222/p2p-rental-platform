package com.team7.rental.payment.repository;

/** 차감 제안 자동 수락 배치 대상 */
public record DeductionAutoAcceptTarget(Long proposalId, Long rentalId) {
}
