package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 예약 취소 (REQ-015). 쓰기는 모두 대여 행 락(findByIdForUpdate)으로 시작해 같은 대여의 취소 요청끼리 한 줄로 선다.
 * 1순위는 상태·수수료 기록만 하고 환불·정산·알림은 하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalCancelService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final CancelFeeCalculator cancelFeeCalculator;
	private final RentalQueryService rentalQueryService;
	private final Clock clock;

	/**
	 * 단독 취소. 취소 종류는 요청자의 역할(RENTER·OWNER)로 정한다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_CANCEL_NOT_ALLOWED
	 */
	@Transactional
	public RentalDetailResponse cancel(Long rentalId, Long memberId, RentalCancelRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		Rental rental = getParticipantRentalForUpdate(rentalId, memberId);
		CancelType type = rental.roleOf(memberId) == RentalRole.RENTER ? CancelType.RENTER : CancelType.OWNER;
		rental.validateCancelable(type, today);
		int cancelFee = cancelFeeCalculator.calculate(rental, type, today);
		cancel(rental, memberId, memberId, type, request.reason(), request.normalizedReasonText(), cancelFee, now);
		return rentalQueryService.getRental(rentalId, memberId);
	}

	private Rental getParticipantRentalForUpdate(Long rentalId, Long memberId) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		return rental;
	}

	// 전이 + 같은 트랜잭션의 이력. changedBy는 상태를 바꾼 회원(MUTUAL은 수락자), canceledBy는 취소를 원한 회원(MUTUAL은 요청자)
	private void cancel(Rental rental, Long canceledBy, Long changedBy, CancelType type, CancelReason reason,
		String reasonText, int cancelFee, LocalDateTime now) {
		RentalStatus from = rental.getStatus();
		rental.cancel(canceledBy, type, reason, reasonText, cancelFee, now);
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rental.getId(), from, rental.getStatus(),
			changedBy, RentalStatusHistory.cancelReasonOf(reason, reasonText), now));
	}
}
