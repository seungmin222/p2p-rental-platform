package com.team7.rental.payment.repository;

/** 조정 배치 대상 */
public record ReconcileTarget(Long paymentId, Long rentalId, String orderId) {
}
