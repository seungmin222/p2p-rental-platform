package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.dto.CancelAgreementAction;
import com.team7.rental.rental.dto.CancelAgreementRespondRequest;
import com.team7.rental.rental.dto.CancelAgreementResponse;
import com.team7.rental.rental.dto.RentalCancelRequest;
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
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/**
 * 예약 취소의 트랜잭션 부분 (REQ-015). 쓰기는 모두 대여 행 락(findByIdForUpdate)으로 시작해 같은 대여의 취소 요청끼리 한 줄로 선다.
 * 취소 전이와 환불 기록을 같이 커밋하고(TX1), PG 호출은 {@link RentalCancelService}가 커밋 뒤에 한다. 대여는 환불 결과와 상관없이 바로 CANCELED다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalCancelTxService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final RentalCancelAgreementRepository rentalCancelAgreementRepository;
	private final CancelFeeCalculator cancelFeeCalculator;
	private final RentalRefundPort rentalRefundPort;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 단독 취소. 취소 종류는 요청자의 역할(RENTER·OWNER)로 정한다.
	 *
	 * @return 커밋 뒤 실행할 환불. 결제 대기·결제 없는 대여면 비어 있다
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_CANCEL_NOT_ALLOWED
	 */
	@Transactional
	public Optional<RefundTicket> cancel(Long rentalId, Long memberId, RentalCancelRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		Rental rental = getParticipantRentalForUpdate(rentalId, memberId);
		CancelType type = rental.roleOf(memberId) == RentalRole.RENTER ? CancelType.RENTER : CancelType.OWNER;
		rental.validateCancelable(type, today);
		int cancelFee = cancelFeeCalculator.calculate(rental, type, today);
		cancel(rental, memberId, memberId, type, request.reason(), request.normalizedReasonText(), cancelFee, now);
		return rentalRefundPort.prepareCancelRefund(rentalId, cancelFee);
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
	 * @return 커밋 뒤 실행할 환불. 거절했거나 결제 없는 대여면 비어 있다
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_CANCEL_AGREEMENT_NOT_FOUND(다른 대여의 요청
	 *     포함), RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE, RENTAL_CANCEL_AGREEMENT_CLOSED(이미 응답·만료),
	 *     RENTAL_INVALID_TRANSITION(수락 시 대여가 RESERVED가 아님)
	 */
	@Transactional
	public Optional<RefundTicket> respondAgreement(Long rentalId, Long agreementId, Long memberId,
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
			return Optional.empty();
		}
		agreement.accept(now);
		int cancelFee = cancelFeeCalculator.calculate(rental, CancelType.MUTUAL, now.toLocalDate());
		cancel(rental, agreement.getRequestedBy(), memberId, CancelType.MUTUAL, agreement.getReason(),
			agreement.getReasonText(), cancelFee, now);
		return rentalRefundPort.prepareCancelRefund(rentalId, cancelFee);
	}

	/**
	 * 만료 배치(CancelAgreementExpireJob)용. 만료 시각이 지난 REQUESTED 동의 요청을 EXPIRED로 바꾼다. 멱등.
	 * 만료 전에 확인을 통과한 응답이 먼저 커밋했으면 상태가 REQUESTED가 아니라 건드리지 않는다.
	 *
	 * @return 만료한 건수
	 */
	@Transactional
	public int expireOverdue(LocalDateTime now) {
		return rentalCancelAgreementRepository.expireOverdue(now);
	}

	/** 인계 없음 자동 취소 배치 대상 id */
	public List<Long> findNoHandoverCancelIds(LocalDate today) {
		return rentalRepository.findNoHandoverCancelIds(today.minusDays(policyProperties.handover().autoCancelDays()));
	}

	/**
	 * 인계 없음 자동 취소 한 건 (REQ-017). 전액 환불, 이력 changedBy = null. 대상 조회 뒤 인계됐을 수 있어 조건을 다시 본다.
	 *
	 * @return canceled: 처리했는지, refund: 커밋 뒤 실행할 환불(결제 없으면 null)
	 */
	@Transactional
	public NoHandoverCancelResult cancelForNoHandover(Long rentalId, LocalDateTime now) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId).orElse(null);
		if (rental == null
			|| !rental.isNoHandoverCancelDue(now.toLocalDate(), policyProperties.handover().autoCancelDays())) {
			return new NoHandoverCancelResult(false, null);
		}
		RentalStatus from = rental.getStatus();
		rental.cancelForNoHandover(now);
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rentalId, from, rental.getStatus(), null,
			RentalStatusHistory.REASON_NO_HANDOVER_BATCH, now));
		return new NoHandoverCancelResult(true, rentalRefundPort.prepareCancelRefund(rentalId, 0).orElse(null));
	}

	public record NoHandoverCancelResult(boolean canceled, RefundTicket refund) {
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
