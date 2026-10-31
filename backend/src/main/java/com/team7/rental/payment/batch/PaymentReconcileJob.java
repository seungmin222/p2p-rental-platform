package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.repository.ReconcileTarget;
import com.team7.rental.payment.service.PaymentReconcileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PG 조정 (REQ-024, policies.md 배치 주기 1시간, 범위 reconciliation.lookback-hours). 건별로 처리해 한 건이 실패해도 나머지는 계속한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

	private final PaymentReconcileService paymentReconcileService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.payment-reconcile-cron}")
	@SchedulerLock(name = "payment.reconcile", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		reconcileRecent(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 대조한 건수
	 */
	public int reconcileRecent(LocalDateTime now) {
		List<ReconcileTarget> targets = paymentReconcileService.findTargets(now);
		int reconciled = 0;
		for (ReconcileTarget target : targets) {
			try {
				paymentReconcileService.reconcile(target);
				reconciled++;
			} catch (RuntimeException e) {
				log.error("Payment reconcile failed: paymentId={}", target.paymentId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Payment reconcile: {} / {} checked", reconciled, targets.size());
		}
		return reconciled;
	}
}
