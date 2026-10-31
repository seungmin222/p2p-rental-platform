package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.rental.dto.RentalPaymentView;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 도메인이 쓰는 대여 쪽 입구. 호출하는 쪽의 트랜잭션에 참여해 "결제 상태 + 대여 상태"를 한 번에 반영한다(docs/domains/payment.md 원칙 3).
 * 시스템이 바꾸므로 이력 changedBy = null이다. 쓰기 트랜잭션은 {@link #lock}으로 시작해 같은 대여의 취소·배치와 한 줄로 선다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RentalPaymentTxService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final Clock clock;

	/**
	 * 행 락을 잡고 읽는다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND
	 */
	public RentalPaymentView lock(Long rentalId) {
		return viewOf(getForUpdate(rentalId), LocalDateTime.now(clock));
	}

	/** 결제 승인: PENDING_PAYMENT → RESERVED */
	public void confirmPayment(Long rentalId, LocalDateTime now) {
		Rental rental = getForUpdate(rentalId);
		RentalStatus from = rental.getStatus();
		rental.confirmPayment();
		saveHistory(rental, from, RentalStatusHistory.REASON_PAYMENT_APPROVED, now);
	}

	/** 결제 만료: PENDING_PAYMENT → CANCELED(PAYMENT_EXPIRED) */
	public void expirePayment(Long rentalId, LocalDateTime now) {
		Rental rental = getForUpdate(rentalId);
		RentalStatus from = rental.getStatus();
		rental.expirePayment(now);
		saveHistory(rental, from, RentalStatusHistory.REASON_PAYMENT_EXPIRED, now);
	}

	/** 금액 불일치: PENDING_PAYMENT → CANCELED(AMOUNT_MISMATCH) */
	public void cancelForAmountMismatch(Long rentalId, LocalDateTime now) {
		Rental rental = getForUpdate(rentalId);
		RentalStatus from = rental.getStatus();
		rental.cancelForAmountMismatch(now);
		saveHistory(rental, from, RentalStatusHistory.REASON_AMOUNT_MISMATCH, now);
	}

	private Rental getForUpdate(Long rentalId) {
		return rentalRepository.findByIdForUpdate(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
	}

	private void saveHistory(Rental rental, RentalStatus from, String reason, LocalDateTime now) {
		rentalStatusHistoryRepository.save(
			RentalStatusHistory.create(rental.getId(), from, rental.getStatus(), null, reason, now));
	}

	private RentalPaymentView viewOf(Rental rental, LocalDateTime now) {
		return new RentalPaymentView(rental.getId(), rental.getItemId(), rental.getRenterId(), rental.getStatus(),
			rental.getRentalFee(), rental.getDeposit(), rental.getOverdueFee(), rental.getCancelFee(),
			rental.isPaymentExpired(now));
	}
}
