package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.service.ExtensionPaymentTxService;
import com.team7.rental.rental.repository.ExtensionRef;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 연장 결제 만료 (REQ-038, policies.md 배치 주기 1분): 결제 대기 연장이 payment.expire-minutes를 넘으면 CANCELED, 묶인 READY 주문은 FAILED.
 * 조회 쪽(점유·연체 배치)이 만료 시각을 보므로 이 배치가 늦어도 기간 선점은 풀린다. 건별 트랜잭션이고 조건을 다시 보므로 멱등하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtensionPaymentExpireJob {

	private final ExtensionPaymentTxService extensionPaymentTxService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.rental-extension-expire-cron}")
	@SchedulerLock(name = "payment.extension-expire", lockAtMostFor = "5m", lockAtLeastFor = "30s")
	public void run() {
		expireDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 처리한 건수
	 */
	public int expireDue(LocalDateTime now) {
		List<ExtensionRef> targets = extensionPaymentTxService.findExpired(now);
		int expired = 0;
		for (ExtensionRef target : targets) {
			try {
				if (extensionPaymentTxService.expire(target, now)) {
					expired++;
				}
			} catch (RuntimeException e) {
				log.error("Extension payment expire failed: extensionId={}", target.extensionId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Extension payment expire: {} / {} expired", expired, targets.size());
		}
		return expired;
	}
}
