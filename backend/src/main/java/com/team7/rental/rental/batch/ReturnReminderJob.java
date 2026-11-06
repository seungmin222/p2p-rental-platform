package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalOverdueService;
import com.team7.rental.rental.service.RentalOverdueTxService.ReminderTarget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 반납 리마인더 (REQ-031, policies.md return.reminder·배치 주기 매일 09:00). RENTING이고 반납 예정일이 내일인 대여의 대여자에게만 알린다.
 * <p>
 * 상태를 바꾸지 않으므로 다시 돌면 같은 event_id로 다시 발행한다. 중복 저장은 알림 구현이 event_id 유니크로 막는다(RentalNotificationPort 계약).
 * 여러 인스턴스가 같은 회차를 두 번 돌지 않게 ShedLock을 건다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnReminderJob {

	private final RentalOverdueService rentalOverdueService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.return-reminder-cron}")
	@SchedulerLock(name = "rental.return-reminder", lockAtMostFor = "30m", lockAtLeastFor = "30s")
	public void run() {
		remindDue(LocalDateTime.now(clock));
	}

	/**
	 * 한 회차 처리. 테스트는 이 메서드를 직접 부른다.
	 *
	 * @return 발행한 건수
	 */
	public int remindDue(LocalDateTime now) {
		List<ReminderTarget> targets = rentalOverdueService.findReturnReminderTargets(now.toLocalDate());
		int sent = 0;
		for (ReminderTarget target : targets) {
			try {
				rentalOverdueService.sendReturnReminder(target);
				sent++;
			} catch (RuntimeException e) {
				log.error("Return reminder failed: rentalId={}", target.rentalId(), e);
			}
		}
		if (!targets.isEmpty()) {
			log.info("Return reminder: {} / {} sent", sent, targets.size());
		}
		return sent;
	}
}
