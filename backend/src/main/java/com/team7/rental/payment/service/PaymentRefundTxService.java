package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentTransactionResult;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.payment.repository.RefundRetryTarget;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.Completion;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/** 환불의 트랜잭션 부분. TX1 = 환불액·시도 기록, TX2 = 결과 반영. PG 호출은 {@link PaymentRefundService}가 그 사이에 한다 */
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentRefundTxService {

	private final PaymentRepository paymentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final RentalPaymentTxService rentalPaymentTxService;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/** TX1. 호출하는 쪽 트랜잭션에 참여한다(대여 전이와 같이 커밋해야 서버가 죽어도 환불이 남는다) */
	public RefundTicket prepare(Payment payment, int amount, RefundPurpose purpose, Completion completion) {
		payment.requestRefund(amount, LocalDateTime.now(clock));
		PaymentTransaction transaction = paymentTransactionRepository.save(PaymentTransaction.pending(payment.getId(),
			PaymentTransactionAction.CANCEL, amount, purpose.keyOf(payment.getRentalId())));
		return ticketOf(payment, transaction, purpose, completion, false);
	}

	/**
	 * 취소 환불 TX1. 호출하는 쪽이 대여 행 락을 잡은 트랜잭션 안에서 부른다. 보증금은 항상 전액 반환이다(policies.md §2).
	 * cancelFee &gt; 0이면 제공자 정산(REQ-039)을 여기서 만들 자리다.
	 */
	public Optional<RefundTicket> prepareCancelRefund(Long rentalId, int cancelFee) {
		List<Payment> payments = paymentRepository.findAllByRentalIdForUpdate(rentalId, PaymentType.RENTAL);
		closeReadyOrders(payments);
		return payments.stream()
			.filter(payment -> payment.getStatus() == PaymentStatus.APPROVED)
			.findFirst()
			// 환불액 0원은 PG를 부르지 않고 상태만 둔다(원칙 7). 보증금이 있으면 0원이 될 수 없어 보증금 상태도 그대로다
			.filter(payment -> payment.getAmount() - cancelFee > 0)
			.map(payment -> prepare(payment, payment.getAmount() - cancelFee, RefundPurpose.CANCEL, null));
	}

	/**
	 * 보증금 반환 TX1 (REQ-025). 호출하는 쪽이 대여 행 락을 잡은 트랜잭션 안에서 부른다.
	 *
	 * @return 비어 있으면 PG가 필요 없다(결제 없음·보증금 0원·반환액 0원이면 FORFEITED)
	 */
	public Optional<RefundTicket> prepareDepositReturn(Long rentalId, int overdueFee, Completion completion) {
		Optional<Payment> frozen = findFrozen(rentalId);
		if (frozen.isEmpty()) {
			return Optional.empty();
		}
		Payment payment = frozen.get();
		int refund = payment.getDepositAmount() - overdueFee;
		if (refund <= 0) {
			payment.forfeitDeposit();
			return Optional.empty();
		}
		return Optional.of(prepare(payment, refund, RefundPurpose.DEPOSIT, completion));
	}

	public boolean hasFrozenDeposit(Long rentalId) {
		return findFrozen(rentalId).isPresent();
	}

	private Optional<Payment> findFrozen(Long rentalId) {
		return paymentRepository.findAllByRentalIdForUpdate(rentalId, PaymentType.RENTAL).stream()
			.filter(Payment::isDepositFrozen)
			.findFirst();
	}

	/**
	 * 대여가 결제 없이 끝났을 때 READY 주문을 FAILED로 닫는다. 승인 결과를 모르는(시도 PENDING) 주문은 PG에 승인됐을 수 있어 조정 배치가 보도록 남긴다.
	 * 호출하는 쪽이 대여 행 락을 잡은 트랜잭션 안에서 부른다.
	 */
	public void closeReadyOrders(Long rentalId) {
		closeReadyOrders(paymentRepository.findAllByRentalIdForUpdate(rentalId, PaymentType.RENTAL));
	}

	private void closeReadyOrders(List<Payment> payments) {
		payments.stream()
			.filter(payment -> payment.getStatus() == PaymentStatus.READY)
			.filter(payment -> !paymentTransactionRepository.existsByPaymentIdAndActionAndResult(payment.getId(),
				PaymentTransactionAction.APPROVE, PaymentTransactionResult.PENDING))
			.forEach(Payment::fail);
	}

	/**
	 * 재시도 배치 대상: 환불이 남았고 관리자 확인 전이며, 마지막 PG 시도 후 refund.retry 간격이 지난 결제. 시도 시각을 보므로 PG 응답이 늦어도 다음 회차를 놓치지 않고, 진행 중인 첫 시도와 겹치지 않으며,
	 * 첫 시도가 서버 중단으로 끝나지 못한 건(REFUND_FAILED가 아니어도)도 찾는다.
	 */
	@Transactional(readOnly = true)
	public List<RefundRetryTarget> findRetryTargets(LocalDateTime now) {
		return paymentRepository.findRefundRetryTargets(now.minus(policyProperties.refund().interval()));
	}

	/**
	 * 재시도 TX1. 환불액을 다시 계산하지 않고 마지막 시도의 금액·키를 그대로 쓴다(같은 키여야 PG가 중복 환불하지 않는다).
	 *
	 * @return 대상 조회 뒤 환불이 끝났으면 비어 있다
	 */
	public Optional<RefundTicket> prepareRetry(RefundRetryTarget target) {
		rentalPaymentTxService.lock(target.rentalId());
		Payment payment = paymentRepository.findByIdForUpdate(target.paymentId()).orElseThrow();
		if (payment.getRefundPendingAmount() == null || payment.isNeedsAdminCheck()) {
			return Optional.empty();
		}
		PaymentTransaction last = paymentTransactionRepository
			.findFirstByPaymentIdAndActionOrderByIdDesc(payment.getId(), PaymentTransactionAction.CANCEL)
			.orElseThrow();
		payment.recordRefundAttempt(LocalDateTime.now(clock));
		PaymentTransaction transaction = paymentTransactionRepository.save(PaymentTransaction.pending(payment.getId(),
			PaymentTransactionAction.CANCEL, last.getAmount(), last.getIdempotencyKey()));
		RefundPurpose purpose = RefundPurpose.ofKey(last.getIdempotencyKey());
		Completion completion = purpose == RefundPurpose.DEPOSIT
			? new Completion(null, RentalStatusHistory.REASON_REFUND_RETRY_BATCH)
			: null;
		return Optional.of(ticketOf(payment, transaction, purpose, completion, true));
	}

	/**
	 * TX2. 이미 반영된 환불이면 시도 기록만 남긴다(요청과 재시도 배치가 겹친 경우). Unknown이면 시도는 PENDING으로 두고 실패처럼 처리한다.
	 * 보증금 반환이 끝나면 대여를 COMPLETED로 바꾼다. 그 자리가 제공자 정산(REQ-039)을 만들 곳이다.
	 */
	public void applyResult(RefundTicket ticket, PgResult result) {
		rentalPaymentTxService.lock(ticket.rentalId());
		Payment payment = paymentRepository.findByIdForUpdate(ticket.paymentId()).orElseThrow();
		PaymentTransaction transaction = paymentTransactionRepository.findById(ticket.transactionId()).orElseThrow();
		boolean pending = payment.getRefundPendingAmount() != null;
		switch (result) {
			case PgResult.Success success -> {
				transaction.succeed(success.toRecord());
				if (pending) {
					payment.applyRefund();
					if (ticket.completion() != null) {
						rentalPaymentTxService.completeAfterDepositReturn(ticket.rentalId(), ticket.completion(),
							LocalDateTime.now(clock));
					}
				}
			}
			case PgResult.Failure failure -> {
				transaction.fail(failure.toRecord());
				markFailed(payment, ticket, pending);
			}
			case PgResult.Unknown unknown -> markFailed(payment, ticket, pending);
		}
	}

	private void markFailed(Payment payment, RefundTicket ticket, boolean pending) {
		if (!pending) {
			return;
		}
		payment.markRefundFailed();
		if (ticket.retry()) {
			payment.countRefundRetryFailure(policyProperties.refund().maxRetry());
		}
	}

	private RefundTicket ticketOf(Payment payment, PaymentTransaction transaction, RefundPurpose purpose,
		Completion completion, boolean retry) {
		return new RefundTicket(payment.getRentalId(), payment.getId(), transaction.getId(), payment.getPaymentKey(),
			transaction.getAmount(), purpose.getCancelReason(), transaction.getIdempotencyKey(), completion, retry);
	}
}
