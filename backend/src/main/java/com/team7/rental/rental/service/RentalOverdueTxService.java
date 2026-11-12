package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/** 연체 전환·반납·인계 리마인더 대상 조회의 트랜잭션 부분 (REQ-023·031·017). 알림 발행은 커밋 뒤 {@link RentalOverdueService}가 한다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalOverdueTxService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final RentalExtensionRepository rentalExtensionRepository;
	private final PolicyProperties policyProperties;

	public List<Long> findOverdueTargetIds(LocalDate today) {
		return rentalRepository.findOverdueTargetIds(today);
	}

	/**
	 * RENTING → OVERDUE 한 건 + 이력(changedBy = null, OVERDUE_BATCH). 대상 조회 뒤 반납됐을 수 있어 행 락을 잡고 조건을 다시 본다.
	 * 반납 요청도 같은 대여 행 락을 잡는다: 배치가 먼저면 반납 요청이 이 커밋을 기다린 뒤 OVERDUE에서 반납하고, 반납이 먼저면 여기서 RETURNED를 보고 건너뛴다.
	 * <p>
	 * 만료 전 결제 대기 연장(REQ-038)이 있으면 건너뛴다(open-issues 사용자 결정 4-B). 결제되면 종료일이 바뀌어 대상이 아니게 되고, 만료되면 매시 재검사가 OVERDUE로 바꾼다.
	 * 연체료는 상태가 아니라 날짜로 계산하므로 늦게 바뀌어도 금액은 같다. 연장 결제 승인도 대여 행 락을 먼저 잡아 이 판단과 한 줄로 선다.
	 *
	 * @return 전환했으면 알림 받을 당사자. 이미 대상이 아니면 비어 있다
	 */
	@Transactional
	public Optional<Participants> markOverdue(Long rentalId, LocalDateTime now) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId).orElse(null);
		LocalDate today = now.toLocalDate();
		if (rental == null || !rental.isOverdueDue(today) || rentalExtensionRepository.existsPending(rentalId, now)) {
			return Optional.empty();
		}
		RentalStatus from = rental.getStatus();
		rental.markOverdue(today);
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rentalId, from, rental.getStatus(), null,
			RentalStatusHistory.REASON_OVERDUE_BATCH, now));
		return Optional.of(new Participants(rentalId, rental.getRenterId(), rental.getOwnerId()));
	}

	/** 반납 리마인더 대상: RENTING이고 endDate = today + 1 */
	public List<ReminderTarget> findReturnReminderTargets(LocalDate today) {
		return rentalRepository.findAllByStatusAndEndDateOrderById(RentalStatus.RENTING, today.plusDays(1)).stream()
			.map(rental -> new ReminderTarget(rental.getId(), rental.getRenterId(), rental.getEndDate()))
			.toList();
	}

	/**
	 * 인계 리마인더 대상: RESERVED이고 오늘 - handover.auto-cancel-days &lt;= startDate &lt; 오늘.
	 * 하한은 같은 09:00 회차에 자동 취소(startDate &lt; 오늘 - auto-cancel-days)되는 건에 리마인더가 먼저 가지 않게 한다.
	 * 하한 안에서 매일 다시 조회되지만 event_id가 대여당 하나라 알림은 1회다(배치를 하루 빠뜨려도 다음 날 보낸다)
	 */
	public List<Participants> findHandoverReminderTargets(LocalDate today) {
		return rentalRepository
			.findHandoverReminderTargets(today.minusDays(policyProperties.handover().autoCancelDays()), today)
			.stream()
			.map(rental -> new Participants(rental.getId(), rental.getRenterId(), rental.getOwnerId()))
			.toList();
	}

	public record Participants(Long rentalId, Long renterId, Long ownerId) {
	}

	public record ReminderTarget(Long rentalId, Long renterId, LocalDate endDate) {
	}
}
