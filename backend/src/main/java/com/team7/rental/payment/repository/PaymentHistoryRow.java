package com.team7.rental.payment.repository;

import java.time.LocalDateTime;

import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentType;

/** 결제·보증금 내역 한 줄 = payments 행 하나 (REQ-033) */
public record PaymentHistoryRow(Long paymentId, Long rentalId, PaymentType type, int amount, int rentalFeeAmount,
	int depositAmount, PaymentStatus status, DepositStatus depositStatus, int refundedAmount,
	Integer refundPendingAmount, DamageGrade damageGrade, Integer deductionAmount, String deductionReason,
	LocalDateTime createdAt) {
}
