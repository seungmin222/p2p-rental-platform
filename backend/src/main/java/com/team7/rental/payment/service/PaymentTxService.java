package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.dto.PaymentConfirmResponse;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentTransactionResult;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.payment.repository.ReconcileTarget;
import com.team7.rental.rental.dto.RentalPaymentView;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제의 트랜잭션 부분 (REQ-024). 모든 쓰기는 대여 행 락 → 결제 행 락 순서로 시작한다. PG 호출은 {@link PaymentService}가 TX1과 TX2 사이에 한다.
 * <p>
 * 대여 행 락을 잡기 전에 일반 SELECT를 하지 않는다. MySQL REPEATABLE READ에서 첫 일반 읽기가 스냅샷을 만들어, 락을 기다린 뒤에도 옛 값을 읽게 된다.
 */
@Slf4j
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

	/**
	 * TX2의 결론.
	 *
	 * @param refund 승인 직후 대여가 만료·취소된 경합이면 이어서 할 전액 취소
	 * @param canceledAfterExpiry 경합이라 PAYMENT_CANCELED_AFTER_EXPIRY로 답한다. 동시에 들어온 다른 요청이 환불을 맡았으면 refund는 null이다
	 */
	record ConfirmOutcome(PaymentConfirmResponse response, RefundTicket refund, boolean canceledAfterExpiry) {
	}

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final RentalPaymentTxService rentalPaymentTxService;
	private final PaymentRefundTxService paymentRefundTxService;
	private final PolicyProperties policyProperties;
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
		Payment payment = paymentRepository
			.findFirstByRentalIdAndTypeAndStatusOrderByIdDesc(rentalId, PaymentType.RENTAL, PaymentStatus.READY)
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
	 * @throws BusinessException PAYMENT_RENTER_ONLY, PAYMENT_INVALID_STATUS(FAILED 주문), PAYMENT_EXPIRED(대여가 이미 만료·취소),
	 *     PAYMENT_CANCELED_AFTER_EXPIRY(경합으로 취소된 주문의 재호출. 첫 응답과 같게 답한다)
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
		if (isCanceledAfterExpiry(payment, rental)) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCELED_AFTER_EXPIRY);
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
			return new ConfirmOutcome(PaymentConfirmResponse.of(payment), null, isCanceledAfterExpiry(payment, rental));
		}
		return approveAndSettle(rental, payment, result.payment().paymentKey(), now);
	}

	// 승인은 됐는데 대여가 RESERVED에 이르지 못했다: 경합으로 환불 중이거나 환불이 남은 건이다
	private boolean isCanceledAfterExpiry(Payment payment, RentalPaymentView rental) {
		return payment.getStatus() == PaymentStatus.APPROVED && !rental.isReservedOrLater();
	}

	private ConfirmOutcome approveAndSettle(RentalPaymentView rental, Payment payment, String paymentKey,
		LocalDateTime now) {
		Long rentalId = rental.rentalId();
		payment.approve(paymentKey);
		if (rental.isPayable()) {
			rentalPaymentTxService.confirmPayment(rentalId, now);
			return new ConfirmOutcome(PaymentConfirmResponse.of(payment), null, false);
		}
		if (rental.status() == RentalStatus.PENDING_PAYMENT) {
			rentalPaymentTxService.expirePayment(rentalId, now);
		}
		RefundTicket refund = paymentRefundTxService.prepare(payment, payment.getAmount(), RefundPurpose.CANCEL,
			null);
		return new ConfirmOutcome(PaymentConfirmResponse.of(payment), refund, true);
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

	/**
	 * 조정 배치 대상: 최근(reconciliation.lookback-hours) 결제 중 payment.expire-minutes 동안 바뀌지 않은 것. 그 시간이 지나면 승인 요청이 끝났다고 볼 수 있어 진행 중인 요청과 겹치지 않는다.
	 */
	@Transactional(readOnly = true)
	public List<ReconcileTarget> findReconcileTargets(LocalDateTime now) {
		return paymentRepository.findReconcileTargets(now.minusHours(policyProperties.reconciliation().lookbackHours()),
			now.minusMinutes(policyProperties.payment().expireMinutes()));
	}

	/**
	 * PG 조회 결과와 대조해 반영한다. 조회 뒤 상태가 바뀌었을 수 있어 행 락을 잡고 다시 본다.
	 * <ul>
	 *   <li>승인 전(READY·FAILED)인데 PG는 승인: 승인 경합과 같게 처리한다(유효하면 확정, 아니면 전액 환불)</li>
	 *   <li>승인 전인데 PG에 승인이 없음: READY면 FAILED로 닫는다</li>
	 *   <li>승인 후: 잔액이 내부 기록(결제 금액 − 누적 취소)과 다르면 고치지 않고 관리자 확인 대상으로 표시한다. 환불이 남아 있으면 재시도 배치 몫이라 건너뛴다</li>
	 * </ul>
	 *
	 * @return 커밋 뒤 실행할 환불
	 */
	public Optional<RefundTicket> reconcile(ReconcileTarget target, PgResult result) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalPaymentView rental = rentalPaymentTxService.lock(target.rentalId());
		Payment payment = paymentRepository.findByIdForUpdate(target.paymentId()).orElseThrow();
		boolean notApproved = payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.FAILED;
		if (result instanceof PgResult.Failure failure && !PgResult.NOT_FOUND.equals(failure.code())) {
			log.warn("Reconcile lookup rejected: paymentId={}, code={}", payment.getId(), failure.code());
			return Optional.empty();
		}
		PgResult.PgPayment pgPayment = result instanceof PgResult.Success success ? success.payment() : null;
		boolean pgApproved = pgPayment != null && pgPayment.isApproved();
		if (notApproved) {
			if (!pgApproved) {
				pendingApprovals(payment).forEach(transaction -> transaction.fail(result.toRecord()));
				if (payment.getStatus() == PaymentStatus.READY) {
					payment.fail();
				}
				return Optional.empty();
			}
			if (pgPayment.balanceAmount() != payment.getAmount()) {
				flag(payment, "approved elsewhere with a different balance");
				return Optional.empty();
			}
			pendingApprovals(payment).forEach(transaction -> transaction.succeed(result.toRecord()));
			return Optional.ofNullable(approveAndSettle(rental, payment, pgPayment.paymentKey(), now).refund());
		}
		if (payment.getRefundPendingAmount() == null
			&& (!pgApproved || pgPayment.balanceAmount() != payment.balance())) {
			flag(payment, "balance mismatch");
		}
		return Optional.empty();
	}

	private List<PaymentTransaction> pendingApprovals(Payment payment) {
		return paymentTransactionRepository.findAllByPaymentIdAndActionAndResult(payment.getId(),
			PaymentTransactionAction.APPROVE, PaymentTransactionResult.PENDING);
	}

	private void flag(Payment payment, String reason) {
		log.error("Payment needs admin check: paymentId={}, reason={}", payment.getId(), reason);
		payment.flagForAdminCheck();
	}

	private PaymentTransaction transactionOf(Long transactionId) {
		return paymentTransactionRepository.findById(transactionId).orElseThrow();
	}
}
