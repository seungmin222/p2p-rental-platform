package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.repository.RefundRetryTarget;
import com.team7.rental.payment.service.PaymentRefundService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환불 재시도 (REQ-025, policies.md 배치 주기 10분). 같은 멱등성 키로 다시 부르므로 응답만 유실됐던 건도 중복 환불되지 않는다.
 * refund.retry 횟수를 다 쓰면 needs_admin_check가 켜지고 대상에서 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRetryJob {

	private final PaymentRefundService paymentRefundService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.refund-retry-cron}")
	@SchedulerLock(name = "payment.refund-retry", lockAtMostFor = "9m", lockAtLeastFor = "30s")
	public void run() {
		retryDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 시도한 건수
	 */
	public int retryDue(LocalDateTime now) {
		List<RefundRetryTarget> targets = paymentRefundService.findRetryTargets(now);
		int attempted = 0;
		for (RefundRetryTarget target : targets) {
			try {
				paymentRefundService.retry(target);
				attempted++;
			} catch (RuntimeException e) {
				log.error("Refund retry failed: paymentId={}", target.paymentId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Refund retry: {} / {} attempted", attempted, targets.size());
		}
		return attempted;
	}
}
