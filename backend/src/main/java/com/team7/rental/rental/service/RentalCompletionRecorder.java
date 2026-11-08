package com.team7.rental.rental.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * RETURNED → COMPLETED의 유일한 입구. 완료·이력·제공자 정산(REQ-039)을 한곳에서 남겨, 완료 경로(상태 확인 '정상'·'이상', 미반납 신고, 보증금 반환 완료,
 * 차감 확정)가 늘어도 정산이 빠지거나 두 번 생기지 않게 한다. 호출하는 쪽의 트랜잭션(대여 행 락 안)에 참여한다.
 */
@Component
@RequiredArgsConstructor
public class RentalCompletionRecorder {

	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final RentalSettlementPort rentalSettlementPort;

	/**
	 * @param changedBy 이력의 변경자. 시스템이면 null
	 * @throws com.team7.rental.global.error.BusinessException RENTAL_INVALID_TRANSITION(RETURNED 아님)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void complete(Rental rental, Long changedBy, String reason, LocalDateTime now) {
		RentalStatus from = rental.getStatus();
		rental.complete(now);
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rental.getId(), from, rental.getStatus(),
			changedBy, reason, now));
		rentalSettlementPort.createForCompletion(rental.getId(), rental.getOwnerId(), rental.getOverdueFee(),
			rental.getCompletedAt());
	}
}
