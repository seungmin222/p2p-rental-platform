package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.dto.PaymentConfirmResponse;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.dto.RentalPaymentView;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/**
 * 결제의 트랜잭션 부분 (REQ-024). 모든 쓰기는 대여 행 락 → 결제 행 락 순서로 시작한다. PG 호출은 {@link PaymentService}가 TX1과 TX2 사이에 한다.
 * <p>
 * 대여 행 락을 잡기 전에 일반 SELECT를 하지 않는다. MySQL REPEATABLE READ에서 첫 일반 읽기가 스냅샷을 만들어, 락을 기다린 뒤에도 옛 값을 읽게 된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentTxService {

	/** TX1의 결론 */
	sealed interface ConfirmDecision {}

	/** 이미 처리됨: PG를 부르지 않고 기존 결과를 돌려준다 */
	record AlreadyProcessed(PaymentConfirmResponse response) implements ConfirmDecision {
	}

	/** 금액 불일치: 결제 FAILED, 대여 CANCELED를 커밋한 뒤 400을 던진다(같은 트랜잭션에서 던지면 롤백된다) */
	record AmountMismatch() implements ConfirmDecision {
	}

	/** PG 승인을 부른다 */
	record Proceed(Long rentalId, Long transactionId, String idempotencyKey, int amount) implements ConfirmDecision {
	}

	/** TX2의 결론. refund가 있으면 승인 직후 대여가 만료·취소된 경합이라 전액 취소를 이어서 한다 */
	record ConfirmOutcome(PaymentConfirmResponse response, RefundTicket refund) {
	}

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final RentalPaymentTxService rentalPaymentTxService;
	private final PaymentRefundTxService paymentRefundTxService;
	private final Clock clock;

	/**
	 * 주문 생성. 진행 중인(READY) 주문이 있으면 그대로 돌려줘 결제창을 다시 열 수 있게 하고, 실패한(FAILED) 주문 뒤에는 새 주문을 만든다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, PAYMENT_RENTER_ONLY, PAYMENT_EXPIRED, RENTAL_INVALID_TRANSITION(결제 대기가 아님)
	 */
	public OrderResult createOrder(Long rentalId, Long memberId) {
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		if (!rental.renterId().equals(memberId)) {
			throw new BusinessException(ErrorCode.PAYMENT_RENTER_ONLY);
		}
		if (rental.status() != RentalStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		if (rental.paymentExpired()) {
			throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
		}
		Payment payment = paymentRepository.findAllByRentalIdForUpdate(rentalId, PaymentType.RENTAL).stream()
			.filter(existing -> existing.getStatus() == PaymentStatus.READY)
			.findFirst()
			.orElseGet(() -> paymentRepository.save(Payment.createForRental(rentalId, UUID.randomUUID().toString(),
				UUID.randomUUID().toString(), rental.rentalFee(), rental.deposit())));
		return new OrderResult(payment.getOrderId(), payment.getAmount(), rental.itemId());
	}

	record OrderResult(String orderId, int amount, Long itemId) {
	}

	/**
	 * 락 전에 대여 ID를 찾는 읽기. 별도 트랜잭션이라 TX1의 스냅샷에 영향을 주지 않는다.
	 *
	 * @throws BusinessException PAYMENT_NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public Long getRentalId(String orderId) {
		return paymentRepository.findRentalIdByOrderId(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	/**
	 * TX1: 검증과 시도 기록. 금액은 저장된 값과 비교한다(서버 계산이 기준, 원칙 1).
	 *
	 * @throws BusinessException PAYMENT_RENTER_ONLY, PAYMENT_INVALID_STATUS(FAILED 주문), PAYMENT_EXPIRED(대여가 이미 만료·취소)
	 */
	public ConfirmDecision prepareConfirm(Long rentalId, Long memberId, String orderId, String paymentKey,
		int requestAmount) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		if (!rental.renterId().equals(memberId)) {
			throw new BusinessException(ErrorCode.PAYMENT_RENTER_ONLY);
		}
		Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElseThrow();
		if (payment.getStatus() == PaymentStatus.FAILED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		if (payment.getStatus() != PaymentStatus.READY) {
			return new AlreadyProcessed(PaymentConfirmResponse.of(payment));
		}
		if (!rental.isPayable()) {
			throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
		}
		if (payment.getAmount() != requestAmount) {
			payment.fail();
			rentalPaymentTxService.cancelForAmountMismatch(rentalId, now);
			return new AmountMismatch();
		}
		payment.recordPaymentKey(paymentKey);
		PaymentTransaction transaction = paymentTransactionRepository.save(PaymentTransaction.pending(payment.getId(),
			PaymentTransactionAction.APPROVE, payment.getAmount(), payment.getIdempotencyKey()));
		return new Proceed(rentalId, transaction.getId(), payment.getIdempotencyKey(), payment.getAmount());
	}

	/**
	 * TX2: 승인 성공 반영. 대여가 아직 유효하면 결제 APPROVED + 대여 RESERVED. 그새 만료 시각이 지났거나 취소됐으면 기간이 다른 예약에 넘어갔을 수 있어
	 * 확정하지 않고 전액 환불을 준비한다.
	 */
	public ConfirmOutcome applyApproved(Long rentalId, String orderId, Long transactionId, PgResult.Success result) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElseThrow();
		transactionOf(transactionId).succeed(result.toRecord());
		if (payment.getStatus() != PaymentStatus.READY && payment.getStatus() != PaymentStatus.FAILED) {
			return new ConfirmOutcome(PaymentConfirmResponse.of(payment), null);
		}
		payment.approve(result.payment().paymentKey());
		if (rental.isPayable()) {
			rentalPaymentTxService.confirmPayment(rentalId, now);
			return new ConfirmOutcome(PaymentConfirmResponse.of(payment), null);
		}
		if (rental.status() == RentalStatus.PENDING_PAYMENT) {
			rentalPaymentTxService.expirePayment(rentalId, now);
		}
		RefundTicket refund = paymentRefundTxService.prepare(payment, payment.getAmount(), RefundPurpose.CANCEL);
		return new ConfirmOutcome(PaymentConfirmResponse.of(payment), refund);
	}

	/** TX2: PG 오류 반영. 대여는 PENDING_PAYMENT를 유지하고 만료 배치가 취소한다 */
	public void applyFailed(Long rentalId, String orderId, Long transactionId, PgResult.Failure result) {
		rentalPaymentTxService.lock(rentalId);
		Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElseThrow();
		transactionOf(transactionId).fail(result.toRecord());
		if (payment.getStatus() == PaymentStatus.READY) {
			payment.fail();
		}
	}

	/** 결제 만료 배치 대상 대여 id */
	@Transactional(readOnly = true)
	public List<Long> findExpiredRentalIds(LocalDateTime now) {
		return rentalPaymentTxService.findPaymentExpiredIds(now);
	}

	/**
	 * 결제 만료 한 건. 대상 조회 뒤 승인·취소됐을 수 있어 조건을 다시 본다.
	 *
	 * @return 처리했으면 true
	 */
	public boolean expire(Long rentalId, LocalDateTime now) {
		RentalPaymentView rental = rentalPaymentTxService.lock(rentalId);
		if (rental.status() != RentalStatus.PENDING_PAYMENT || !rental.paymentExpired()) {
			return false;
		}
		rentalPaymentTxService.expirePayment(rentalId, now);
		paymentRefundTxService.closeReadyOrders(rentalId);
		return true;
	}

	private PaymentTransaction transactionOf(Long transactionId) {
		return paymentTransactionRepository.findById(transactionId).orElseThrow();
	}
}
