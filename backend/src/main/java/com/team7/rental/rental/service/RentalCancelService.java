package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.dto.CancelAgreementAction;
import com.team7.rental.rental.dto.CancelAgreementRespondRequest;
import com.team7.rental.rental.dto.CancelAgreementResponse;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
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
	private final RentalCancelAgreementRepository rentalCancelAgreementRepository;
	private final CancelFeeCalculator cancelFeeCalculator;
	private final RentalQueryService rentalQueryService;
	private final PolicyProperties policyProperties;
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

	/**
	 * 양측 동의 취소 요청. RESERVED이고 오늘 ≥ 시작일일 때 당사자 한쪽이 보낸다. 거절·만료된 뒤에는 다시 보낼 수 있다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION(RESERVED 아님),
	 *     RENTAL_CANCEL_AGREEMENT_TOO_EARLY(시작일 전, 단독 취소 대상), RENTAL_CANCEL_AGREEMENT_IN_PROGRESS
	 */
	@Transactional
	public CancelAgreementResponse requestAgreement(Long rentalId, Long memberId, RentalCancelRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = getParticipantRentalForUpdate(rentalId, memberId);
		if (rental.getStatus() != RentalStatus.RESERVED) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		if (now.toLocalDate().isBefore(rental.getStartDate())) {
			throw new BusinessException(ErrorCode.RENTAL_CANCEL_AGREEMENT_TOO_EARLY);
		}
		// 만료 배치가 아직 돌지 않은 REQUESTED 행은 진행 중으로 보지 않는다(findOpen이 expiresAt > now까지 본다)
		if (rentalCancelAgreementRepository.findOpen(rentalId, now).isPresent()) {
			throw new BusinessException(ErrorCode.RENTAL_CANCEL_AGREEMENT_IN_PROGRESS);
		}
		LocalDateTime expiresAt = now.plusHours(policyProperties.cancelAgreement().expireHours());
		RentalCancelAgreement agreement = rentalCancelAgreementRepository.save(RentalCancelAgreement.request(
			rentalId, memberId, request.reason(), request.normalizedReasonText(), expiresAt));
		return CancelAgreementResponse.of(agreement, memberId);
	}

	/**
	 * 양측 동의 취소 응답. 요청하지 않은 쪽만 응답한다. ACCEPT → 대여 CANCELED(MUTUAL, 요청자의 사유), REJECT → 요청만 종료.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_CANCEL_AGREEMENT_NOT_FOUND(다른 대여의 요청
	 *     포함), RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE, RENTAL_CANCEL_AGREEMENT_CLOSED(이미 응답·만료),
	 *     RENTAL_INVALID_TRANSITION(수락 시 대여가 RESERVED가 아님)
	 */
	@Transactional
	public RentalDetailResponse respondAgreement(Long rentalId, Long agreementId, Long memberId,
		CancelAgreementRespondRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = getParticipantRentalForUpdate(rentalId, memberId);
		RentalCancelAgreement agreement = rentalCancelAgreementRepository.findByIdAndRentalId(agreementId, rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_CANCEL_AGREEMENT_NOT_FOUND));
		if (agreement.isRequestedBy(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE);
		}
		if (request.action() == CancelAgreementAction.REJECT) {
			agreement.reject(now);
			return rentalQueryService.getRental(rentalId, memberId);
		}
		agreement.accept(now);
		int cancelFee = cancelFeeCalculator.calculate(rental, CancelType.MUTUAL, now.toLocalDate());
		cancel(rental, agreement.getRequestedBy(), memberId, CancelType.MUTUAL, agreement.getReason(),
			agreement.getReasonText(), cancelFee, now);
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
