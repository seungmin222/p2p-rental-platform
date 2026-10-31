package com.team7.rental.payment.repository;

/** 환불 재시도 배치 대상 */
public record RefundRetryTarget(Long paymentId, Long rentalId) {
}
