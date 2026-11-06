package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalOverdueService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 연체 감지 (REQ-023·031, policies.md 배치 주기 매일 00:05 + 매시 정각 재검사). RENTING이고 반납 예정일이 지난 대여를 OVERDUE로 바꾸고
 * 대여자·제공자에게 연체 알림을 보낸다. "오늘"은 Clock(Asia/Seoul) 기준이다.
 * <p>
 * 건별 트랜잭션이라 한 건이 실패해도 나머지는 계속하고, 실패한 건은 다음 회차에 다시 대상이 된다(조건을 다시 보므로 멱등). 두 cron이 같은 락 이름을
 * 쓰므로 겹쳐도 한 번만 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueDetectJob {

	private final RentalOverdueService rentalOverdueService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.overdue-detect-cron}")
	@Scheduled(cron = "${app.batch.overdue-recheck-cron}")
	@SchedulerLock(name = "rental.overdue-detect", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		markDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다(run은 ShedLock 프록시가 lockAtLeastFor 동안 다시 실행하지 않는다).
	 *
	 * @return OVERDUE로 바꾼 건수
	 */
	public int markDue(LocalDateTime now) {
		List<Long> targetIds = rentalOverdueService.findOverdueTargetIds(now.toLocalDate());
		int marked = 0;
		for (Long rentalId : targetIds) {
			try {
				if (rentalOverdueService.markOverdue(rentalId, now)) {
					marked++;
				}
			} catch (RuntimeException e) {
				log.error("Overdue detect failed: rentalId={}", rentalId, e);
			}
		}
		if (!targetIds.isEmpty()) {
			log.info("Overdue detect: {} / {} marked", marked, targetIds.size());
		}
		return marked;
	}
}
