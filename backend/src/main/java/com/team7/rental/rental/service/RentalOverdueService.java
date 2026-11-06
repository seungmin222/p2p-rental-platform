package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.rental.service.RentalOverdueTxService.Participants;
import com.team7.rental.rental.service.RentalOverdueTxService.ReminderTarget;

import lombok.RequiredArgsConstructor;

/**
 * 연체 감지·반납 리마인더 (REQ-023·031). 전환은 {@link RentalOverdueTxService}의 건별 트랜잭션에서 하고, 알림은 커밋 뒤에 보낸다.
 * <p>
 * 커밋과 발행 사이에 서버가 멈추면 그 건의 연체 알림은 빠진다(다음 회차에는 이미 OVERDUE라 대상이 아니다).
 * notification.md "실시간 실패 시 재전송하지 않는다"와 같은 수준으로 받아들인다. 결정은 docs/prompts/feat/rental-overdue/notes.md
 */
@Service
@RequiredArgsConstructor
public class RentalOverdueService {

	private final RentalOverdueTxService rentalOverdueTxService;
	private final RentalNotificationPort rentalNotificationPort;

	public List<Long> findOverdueTargetIds(LocalDate today) {
		return rentalOverdueTxService.findOverdueTargetIds(today);
	}

	/**
	 * 한 건을 OVERDUE로 바꾸고 대여자·제공자에게 OVERDUE 알림(event_id {@code OVERDUE:{rentalId}})을 보낸다.
	 *
	 * @return 전환했으면 true, 이미 대상이 아니면 false
	 */
	public boolean markOverdue(Long rentalId, LocalDateTime now) {
		Optional<Participants> transitioned = rentalOverdueTxService.markOverdue(rentalId, now);
		transitioned.ifPresent(participants -> {
			String eventId = RentalNotificationPort.overdueEventId(participants.rentalId());
			rentalNotificationPort.send(participants.renterId(), Type.OVERDUE, TargetType.RENTAL,
				participants.rentalId(), eventId);
			rentalNotificationPort.send(participants.ownerId(), Type.OVERDUE, TargetType.RENTAL,
				participants.rentalId(), eventId);
		});
		return transitioned.isPresent();
	}

	public List<ReminderTarget> findReturnReminderTargets(LocalDate today) {
		return rentalOverdueTxService.findReturnReminderTargets(today);
	}

	/** 대여자에게만 RETURN_REMINDER (event_id {@code RETURN_REMINDER:{rentalId}:{endDate}}). 상태는 바꾸지 않는다 */
	public void sendReturnReminder(ReminderTarget target) {
		rentalNotificationPort.send(target.renterId(), Type.RETURN_REMINDER, TargetType.RENTAL, target.rentalId(),
			RentalNotificationPort.returnReminderEventId(target.rentalId(), target.endDate()));
	}
}
