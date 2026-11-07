package com.team7.rental.payment.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.payment.repository.DeductionAutoAcceptTarget;
import com.team7.rental.payment.service.DepositDeductionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 차감 제안 자동 수락 (REQ-049, policies.md 배치 주기 1시간). PROPOSED이고 deduction.renter-response-hours가 지난 제안을 AUTO_ACCEPTED로 바꾸고
 * 확정 처리한다(이력 changedBy = null). 건마다 대여 행 락을 잡고 조건을 다시 보므로 대여자 응답·관리자 확정과 겹쳐도, 두 번 돌아도 한 번만 확정된다.
 * <p>
 * "보류 7일 초과 표시"는 바꿀 상태가 없어 배치를 두지 않고 관리자 목록이 held_at으로 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeductionAutoAcceptJob {

	private final DepositDeductionService depositDeductionService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.deduction-auto-accept-cron}")
	@SchedulerLock(name = "payment.deduction-auto-accept", lockAtMostFor = "50m", lockAtLeastFor = "30s")
	public void run() {
		acceptDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 확정한 건수
	 */
	public int acceptDue(LocalDateTime now) {
		List<DeductionAutoAcceptTarget> targets = depositDeductionService.findAutoAcceptTargets(now);
		int accepted = 0;
		for (DeductionAutoAcceptTarget target : targets) {
			try {
				if (depositDeductionService.autoAccept(target, now)) {
					accepted++;
				}
			} catch (RuntimeException e) {
				log.error("Deduction auto-accept failed: proposalId={}", target.proposalId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Deduction auto-accept: {} / {} accepted", accepted, targets.size());
		}
		return accepted;
	}
}
