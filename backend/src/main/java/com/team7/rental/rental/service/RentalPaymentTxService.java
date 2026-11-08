package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.rental.dto.RentalDepositView;
import com.team7.rental.rental.dto.RentalExtensionConfirmed;
import com.team7.rental.rental.dto.RentalExtensionPaymentView;
import com.team7.rental.rental.dto.RentalPaymentView;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ExtensionRef;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalExtensionRepository;
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
	private final ItemInspectionRepository itemInspectionRepository;
	private final RentalCompletionRecorder rentalCompletionRecorder;
	private final RentalExtensionRepository rentalExtensionRepository;
	private final RentalExtensionRecorder rentalExtensionRecorder;
	private final Clock clock;

	/**
	 * 행 락을 잡고 읽는다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND
	 */
	public RentalPaymentView lock(Long rentalId) {
		return viewOf(getForUpdate(rentalId), LocalDateTime.now(clock));
	}

	/** 결제 만료 배치 대상 id */
	@Transactional(readOnly = true)
	public List<Long> findPaymentExpiredIds(LocalDateTime now) {
		return rentalRepository.findPaymentExpiredIds(now);
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

	/**
	 * 연장 행 락 (REQ-038). 호출하는 쪽이 {@link #lock}으로 대여 행 락을 먼저 잡는다(대여 → 연장 → 결제 순서).
	 *
	 * @throws BusinessException RENTAL_EXTENSION_NOT_FOUND: 없거나 다른 대여의 연장일 때
	 */
	public RentalExtensionPaymentView lockExtension(Long rentalId, Long extensionId) {
		RentalExtension extension = rentalExtensionRepository.findByIdForUpdate(extensionId)
			.filter(found -> found.getRentalId().equals(rentalId))
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_EXTENSION_NOT_FOUND));
		return extensionViewOf(extension);
	}

	/** 주문에 묶인 연장의 행 락. 대여 행 락 뒤에 부른다. 연장 주문이 아니면 비어 있다 */
	public Optional<RentalExtensionPaymentView> lockExtensionByPayment(Long paymentId) {
		return rentalExtensionRepository.findByPaymentIdForUpdate(paymentId).map(this::extensionViewOf);
	}

	/** 새 연장 주문을 연장에 묶는다. 연장 행 락 안에서 부른다 */
	public void attachExtensionOrder(Long extensionId, Long paymentId) {
		getExtension(extensionId).attachOrder(paymentId);
	}

	/**
	 * 연장 결제 승인: 연장 CONFIRMED + 대여 RENTING → RENTING(종료일 갱신, 연장 횟수 + 1) + 이력(changedBy = null).
	 * 호출하는 쪽이 {@link RentalExtensionPaymentView#applicable()}을 먼저 본다.
	 */
	public RentalExtensionConfirmed confirmExtension(Long rentalId, Long extensionId, Long paymentId,
		LocalDateTime now) {
		Rental rental = getForUpdate(rentalId);
		RentalExtension extension = getExtension(extensionId);
		rentalExtensionRecorder.apply(rental, extension, null, now);
		extension.confirm(paymentId);
		return new RentalExtensionConfirmed(rentalId, extensionId, rental.getRenterId(), rental.getOwnerId());
	}

	/** 연장 결제 대기 → CANCELED (만료·금액 불일치·승인 시점에 확정 불가). 대여 행 락 → 연장 행 락 안에서 부른다 */
	public void cancelExtension(Long extensionId) {
		getExtension(extensionId).cancel();
	}

	/** 연장 결제 만료 배치 대상 (대여 ID와 함께, 배치가 대여 행 락부터 잡도록) */
	@Transactional(readOnly = true)
	public List<ExtensionRef> findExpiredExtensions(LocalDateTime now) {
		return rentalExtensionRepository.findExpiredRefs(now);
	}

	private RentalExtension getExtension(Long extensionId) {
		return rentalExtensionRepository.findById(extensionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_EXTENSION_NOT_FOUND));
	}

	private RentalExtensionPaymentView extensionViewOf(RentalExtension extension) {
		Rental rental = getForUpdate(extension.getRentalId());
		LocalDateTime now = LocalDateTime.now(clock);
		return new RentalExtensionPaymentView(extension.getId(), extension.getRentalId(), extension.getStatus(),
			extension.getAdditionalFee(), extension.getPaymentId(), extension.isPendingAt(now),
			rental.canApplyExtension(extension.getPrevEndDate()));
	}

	/** 보증금 처리 완료: RETURNED → COMPLETED (REQ-025 반환, REQ-049 차감 확정). 제공자 정산(REQ-039)도 같은 트랜잭션에서 만든다 */
	public void completeAfterDepositReturn(Long rentalId, RentalRefundPort.Completion completion, LocalDateTime now) {
		rentalCompletionRecorder.complete(getForUpdate(rentalId), completion.changedBy(), completion.reason(), now);
	}

	/**
	 * 보증금 보류·차감용으로 행 락을 잡고 읽는다 (REQ-049). 차감 쓰기 트랜잭션은 이것으로 시작한다(대여 행 락 → 결제 행 락).
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND
	 */
	public RentalDepositView lockForDeposit(Long rentalId) {
		return depositViewOf(getForUpdate(rentalId));
	}

	/**
	 * 보증금 차감 조회·사전 검사용 일반 읽기 (REQ-049).
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public RentalDepositView getDepositView(Long rentalId) {
		return depositViewOf(rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND)));
	}

	/** 보류 목록용 일괄 읽기. 없는 ID는 담지 않는다 */
	@Transactional(readOnly = true)
	public Map<Long, RentalDepositView> getDepositViews(Collection<Long> rentalIds) {
		if (rentalIds.isEmpty()) {
			return Map.of();
		}
		Set<Long> lostReported = itemInspectionRepository.findLostReportedRentalIds(rentalIds);
		Map<Long, RentalDepositView> views = new HashMap<>();
		for (Rental rental : rentalRepository.findAllById(rentalIds)) {
			views.put(rental.getId(), depositViewOf(rental, lostReported.contains(rental.getId())));
		}
		return views;
	}

	private RentalDepositView depositViewOf(Rental rental) {
		boolean lostReported = itemInspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN)
			.map(ItemInspection::isLostReported)
			.orElse(false);
		return depositViewOf(rental, lostReported);
	}

	private RentalDepositView depositViewOf(Rental rental, boolean lostReported) {
		return new RentalDepositView(rental.getId(), rental.getItemId(), rental.getOwnerId(), rental.getRenterId(),
			rental.getStatus(), rental.getDeposit(), rental.getOverdueFee(), lostReported);
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
