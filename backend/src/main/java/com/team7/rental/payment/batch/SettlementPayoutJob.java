package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.service.SettlementTxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 지급 (REQ-039, policies.md 배치 주기 매일). SCHEDULED이고 지급 예정일이 오늘 이전인 정산을 PAID로 바꾼다. 실제 송금은 없다.
 * 건마다 행 락을 잡고 조건을 다시 보므로 두 번 돌아도 한 번만 지급된다. HELD(문의 분쟁, REQ-063)는 대상이 아니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPayoutJob {

	private final SettlementTxService settlementTxService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.settlement-payout-cron}")
	@SchedulerLock(name = "payment.settlement-payout", lockAtMostFor = "50m", lockAtLeastFor = "30s")
	public void run() {
		payDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 지급한 건수
	 */
	public int payDue(LocalDateTime now) {
		List<Long> targets = settlementTxService.findPayableIds(now.toLocalDate());
		int paid = 0;
		for (Long settlementId : targets) {
			try {
				if (settlementTxService.pay(settlementId, now)) {
					paid++;
				}
			} catch (RuntimeException e) {
				log.error("Settlement payout failed: settlementId={}", settlementId, e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Settlement payout: {} / {} paid", paid, targets.size());
		}
		return paid;
	}
}
