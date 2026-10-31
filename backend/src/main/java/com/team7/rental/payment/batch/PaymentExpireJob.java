package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.service.PaymentTxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 만료 (REQ-024, policies.md 배치 주기 1분). 조회 쪽이 만료 조건을 보므로 이 배치가 늦어도 기간 선점은 풀린다.
 * 건별 트랜잭션이라 한 건이 실패해도 나머지는 계속하고, 조건을 다시 보므로 멱등하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpireJob {

	private final PaymentTxService paymentTxService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.payment-expire-cron}")
	@SchedulerLock(name = "payment.expire", lockAtMostFor = "5m", lockAtLeastFor = "30s")
	public void run() {
		expireDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 처리한 건수
	 */
	public int expireDue(LocalDateTime now) {
		List<Long> targetIds = paymentTxService.findExpiredRentalIds(now);
		int expired = 0;
		for (Long rentalId : targetIds) {
			try {
				if (paymentTxService.expire(rentalId, now)) {
					expired++;
				}
			} catch (RuntimeException e) {
				log.error("Payment expire failed: rentalId={}", rentalId, e);
			}
		}
		if (!targetIds.isEmpty()) {
			log.info("Payment expire: {} / {} expired", expired, targetIds.size());
		}
		return expired;
	}
}
