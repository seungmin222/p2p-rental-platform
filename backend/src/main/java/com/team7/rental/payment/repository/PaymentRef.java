package com.team7.rental.payment.repository;

import com.team7.rental.payment.entity.PaymentType;

/** 주문의 결제·대여 ID와 종류 */
public record PaymentRef(Long paymentId, Long rentalId, PaymentType type) {
}
