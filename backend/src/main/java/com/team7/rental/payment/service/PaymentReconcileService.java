package com.team7.rental.payment.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.team7.rental.payment.client.PaymentGateway;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.repository.ReconcileTarget;

import lombok.RequiredArgsConstructor;

/**
 * PG 조정 (docs/domains/payment.md 원칙 5). 트랜잭션 밖에서 PG를 조회하고, 결과 반영은 {@link PaymentTxService#reconcile}이 대여·결제 행 락 안에서 한다.
 * 승인 응답 유실로 남은 결제를 끝내고, 고칠 수 없는 불일치는 관리자 확인 대상으로 표시한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentReconcileService {

	private final PaymentGateway paymentGateway;
	private final PaymentTxService paymentTxService;
	private final PaymentRefundService paymentRefundService;

	public List<ReconcileTarget> findTargets(LocalDateTime now) {
		return paymentTxService.findReconcileTargets(now);
	}

	/** 한 건 대조. PG 결과를 모르면 다음 회차에 다시 본다 */
	public void reconcile(ReconcileTarget target) {
		PgResult result = paymentGateway.findByOrderId(target.orderId());
		if (result instanceof PgResult.Unknown) {
			return;
		}
		paymentTxService.reconcile(target, result).ifPresent(paymentRefundService::execute);
	}
}
