package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/** 연체 전환·반납 리마인더 대상 조회의 트랜잭션 부분 (REQ-023·031). 알림 발행은 커밋 뒤 {@link RentalOverdueService}가 한다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalOverdueTxService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;

	public List<Long> findOverdueTargetIds(LocalDate today) {
		return rentalRepository.findOverdueTargetIds(today);
	}

	/**
	 * RENTING → OVERDUE 한 건 + 이력(changedBy = null, OVERDUE_BATCH). 대상 조회 뒤 반납됐을 수 있어 행 락을 잡고 조건을 다시 본다.
	 * 반납 요청은 행 락 없이 @Version으로 막으므로, 이 전환이 먼저 커밋되면 겹친 반납 요청은 버전 충돌(409)로 끝나고 다시 시도하면 OVERDUE에서 반납된다.
	 *
	 * @return 전환했으면 알림 받을 당사자. 이미 대상이 아니면 비어 있다
	 */
	@Transactional
	public Optional<Participants> markOverdue(Long rentalId, LocalDateTime now) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId).orElse(null);
		LocalDate today = now.toLocalDate();
		if (rental == null || !rental.isOverdueDue(today)) {
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

	public record Participants(Long rentalId, Long renterId, Long ownerId) {
	}

	public record ReminderTarget(Long rentalId, Long renterId, LocalDate endDate) {
	}
}
