package com.team7.rental.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/** 환불의 트랜잭션 부분. TX1 = 환불액·시도 기록, TX2 = 결과 반영. PG 호출은 {@link PaymentRefundService}가 그 사이에 한다 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentRefundTxService {

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final RentalPaymentTxService rentalPaymentTxService;

	/** TX1. 호출하는 쪽 트랜잭션에 참여한다(대여 전이와 같이 커밋해야 서버가 죽어도 환불이 남는다) */
	public RefundTicket prepare(Payment payment, int amount, RefundPurpose purpose) {
		payment.requestRefund(amount);
		PaymentTransaction transaction = paymentTransactionRepository.save(PaymentTransaction.pending(payment.getId(),
			PaymentTransactionAction.CANCEL, amount, purpose.keyOf(payment.getRentalId())));
		return new RefundTicket(payment.getRentalId(), payment.getId(), transaction.getId(), payment.getPaymentKey(),
			amount, purpose.getCancelReason(), transaction.getIdempotencyKey());
	}

	/**
	 * TX2. 이미 반영된 환불이면 시도 기록만 남긴다(요청과 재시도 배치가 겹친 경우). Unknown이면 시도는 PENDING으로 두고 실패처럼 처리한다.
	 */
	public void applyResult(RefundTicket ticket, PgResult result) {
		rentalPaymentTxService.lock(ticket.rentalId());
		Payment payment = paymentRepository.findByIdForUpdate(ticket.paymentId()).orElseThrow();
		PaymentTransaction transaction = paymentTransactionRepository.findById(ticket.transactionId()).orElseThrow();
		switch (result) {
			case PgResult.Success success -> {
				transaction.succeed(success.toRecord());
				if (payment.getRefundPendingAmount() != null) {
					payment.applyRefund();
				}
			}
			case PgResult.Failure failure -> {
				transaction.fail(failure.toRecord());
				if (payment.getRefundPendingAmount() != null) {
					payment.markRefundFailed();
				}
			}
			case PgResult.Unknown unknown -> {
				if (payment.getRefundPendingAmount() != null) {
					payment.markRefundFailed();
				}
			}
		}
	}
}
