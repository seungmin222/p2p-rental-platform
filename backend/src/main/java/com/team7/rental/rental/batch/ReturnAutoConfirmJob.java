package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalInspectionTxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동 상태 확인 (REQ-018, policies.md 배치 주기 1시간). 반납 요청 후 return.auto-confirm-days가 지나도록 제공자가 판정하지 않은 대여를
 * 정상으로 처리한다(1순위는 바로 COMPLETED).
 * <p>
 * 건마다 전이와 이력이 필요해 벌크 UPDATE 대신 건별 트랜잭션으로 처리한다. 한 건이 실패해도 나머지는 계속하고, 실패한 건은 다음 회차에 다시
 * 대상이 된다(조건을 다시 보므로 멱등). 제공자 확인과 겹친 낙관적 락 충돌은 제공자가 이긴 것이라 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnAutoConfirmJob {

	private final RentalInspectionTxService rentalInspectionTxService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.return-auto-confirm-cron}")
	@SchedulerLock(name = "rental.return-auto-confirm", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		confirmDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다(run은 ShedLock 프록시가 lockAtLeastFor 동안 다시 실행하지 않는다).
	 *
	 * @return 처리한 건수
	 */
	public int confirmDue(LocalDateTime now) {
		List<Long> targetIds = rentalInspectionTxService.findAutoConfirmTargetIds(now);
		int confirmed = 0;
		for (Long rentalId : targetIds) {
			try {
				if (rentalInspectionTxService.autoConfirm(rentalId, now)) {
					confirmed++;
				}
			} catch (ObjectOptimisticLockingFailureException e) {
				log.info("Return auto-confirm skipped by concurrent update: rentalId={}", rentalId);
			} catch (RuntimeException e) {
				log.error("Return auto-confirm failed: rentalId={}", rentalId, e);
			}
		}
		if (!targetIds.isEmpty()) {
			log.info("Return auto-confirm: {} / {} confirmed", confirmed, targetIds.size());
		}
		return confirmed;
	}
}
