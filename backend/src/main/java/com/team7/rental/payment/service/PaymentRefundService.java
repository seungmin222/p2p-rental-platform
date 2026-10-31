package com.team7.rental.payment.service;

import org.springframework.stereotype.Service;

import com.team7.rental.payment.client.PaymentGateway;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.rental.service.RentalRefundPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환불 실행 (docs/domains/payment.md 원칙 3·8). 외부 API로 열지 않고 서버 안에서만 부른다. 트랜잭션을 열지 않고 PG를 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService implements RentalRefundPort {

	private final PaymentGateway paymentGateway;
	private final PaymentRefundTxService paymentRefundTxService;

	@Override
	public void execute(RefundTicket ticket) {
		PgResult result = paymentGateway.cancel(ticket.paymentKey(), ticket.amount(), ticket.cancelReason(),
			ticket.idempotencyKey());
		if (!(result instanceof PgResult.Success)) {
			log.warn("Refund not completed: paymentId={}, result={}", ticket.paymentId(), result.toRecord());
		}
		paymentRefundTxService.applyResult(ticket, result);
	}
}
