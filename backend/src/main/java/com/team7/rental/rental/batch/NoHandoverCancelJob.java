package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalCancelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인계 없음 자동 취소 (REQ-017, policies.md 배치 주기 매일 09:00). 시작일 + handover.auto-cancel-days까지 인계 확인이 없는 대여를 취소하고 전액 환불한다.
 * 건별 트랜잭션이라 한 건이 실패해도 나머지는 계속하고, 조건을 다시 보므로 멱등하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoHandoverCancelJob {

	private final RentalCancelService rentalCancelService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.no-handover-cancel-cron}")
	@SchedulerLock(name = "rental.no-handover-cancel", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		cancelDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 처리한 건수
	 */
	public int cancelDue(LocalDateTime now) {
		List<Long> targetIds = rentalCancelService.findNoHandoverCancelIds(now.toLocalDate());
		int canceled = 0;
		for (Long rentalId : targetIds) {
			try {
				if (rentalCancelService.cancelForNoHandover(rentalId, now)) {
					canceled++;
				}
			} catch (RuntimeException e) {
				log.error("No-handover cancel failed: rentalId={}", rentalId, e);
			}
		}
		if (!targetIds.isEmpty()) {
			log.info("No-handover cancel: {} / {} canceled", canceled, targetIds.size());
		}
		return canceled;
	}
}
