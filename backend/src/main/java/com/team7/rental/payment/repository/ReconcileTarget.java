package com.team7.rental.payment.repository;

import com.team7.rental.payment.entity.PaymentType;

/** 조정 배치 대상 */
public record ReconcileTarget(Long paymentId, Long rentalId, String orderId, PaymentType type) {
}
