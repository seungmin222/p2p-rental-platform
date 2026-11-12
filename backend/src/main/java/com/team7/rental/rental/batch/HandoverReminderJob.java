package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalOverdueService;
import com.team7.rental.rental.service.RentalOverdueTxService.Participants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인계 리마인더 (REQ-017, policies.md handover.reminder·배치 주기 매일 09:00). 시작일이 지났는데 인계 확인이 없는 RESERVED 대여의
 * 대여자·제공자에게 알린다. 자동 취소 대상(시작일 + handover.auto-cancel-days 경과)은 조회에서 뺀다.
 * <p>
 * 상태를 바꾸지 않으므로 다시 돌면 같은 event_id로 다시 발행한다. 중복 저장은 알림 구현이 (member_id, event_id) 유니크로 막는다(RentalNotificationPort 계약).
 * 여러 인스턴스가 같은 회차를 두 번 돌지 않게 ShedLock을 건다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoverReminderJob {

	private final RentalOverdueService rentalOverdueService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.handover-reminder-cron}")
	@SchedulerLock(name = "rental.handover-reminder", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		remindDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 알림을 보낸 대여 건수
	 */
	public int remindDue(LocalDateTime now) {
		List<Participants> targets = rentalOverdueService.findHandoverReminderTargets(now.toLocalDate());
		int sent = 0;
		for (Participants target : targets) {
			try {
				rentalOverdueService.sendHandoverReminder(target);
				sent++;
			} catch (RuntimeException e) {
				log.error("Handover reminder failed: rentalId={}", target.rentalId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Handover reminder: {} / {} sent", sent, targets.size());
		}
		return sent;
	}
}
