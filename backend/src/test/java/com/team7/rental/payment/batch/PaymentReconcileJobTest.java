package com.team7.rental.payment.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentTransactionResult;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** PG 조정 배치 (REQ-024). payment.expire-minutes = 15, reconciliation.lookback-hours = 24 */
class PaymentReconcileJobTest extends IntegrationTestSupport {

	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDateTime RUN_AT = CREATED_AT.plusHours(1);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final int AMOUNT = 60_000;

	@Autowired
	private PaymentReconcileJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(CREATED_AT);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("결과를 몰랐던 결제가 PG에 승인돼 있고 대여가 이미 끝났으면 전액 취소한다")
	void approvedAfterRentalEnded() {
		// given
		Rental rental = pendingRental(CREATED_AT.plusMinutes(15));
		Payment payment = unknownApproval(rental);
		paymentGateway.seedApproved("pay-1", payment.getOrderId(), AMOUNT);
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then: 만료된 대여는 취소하고 승인된 금액은 돌려준다
		Payment refunded = reload(payment);
		assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(refunded.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(AMOUNT);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.CANCELED);
		assertThat(pendingApprovals()).isZero();
	}

	@Test
	@DisplayName("결과를 몰랐던 결제가 PG에 승인돼 있고 대여가 아직 유효하면 확정한다")
	void approvedAndStillPayable() {
		// given
		Rental rental = pendingRental(RUN_AT.plusMinutes(5));
		Payment payment = unknownApproval(rental);
		paymentGateway.seedApproved("pay-1", payment.getOrderId(), AMOUNT);
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RESERVED);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("PG에 없으면 승인되지 않은 것이라 FAILED로 닫고 시도도 FAIL이다")
	void notFoundFails() {
		// given
		Payment payment = unknownApproval(pendingRental(CREATED_AT.plusMinutes(15)));
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.FAILED);
		assertThat(pendingApprovals()).isZero();
	}

	@Test
	@DisplayName("PG 조회 결과를 모르면 그대로 두고 다음 회차에 다시 본다")
	void unknownLookupSkipped() {
		// given
		Payment payment = unknownApproval(pendingRental(CREATED_AT.plusMinutes(15)));
		paymentGateway.failNextLookup();
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then
		assertThat(reload(payment).getStatus()).isEqualTo(PaymentStatus.READY);
		assertThat(pendingApprovals()).isEqualTo(1);
	}

	@Test
	@DisplayName("승인된 결제의 잔액이 맞으면 그대로고, 다르면 고치지 않고 관리자 확인 대상으로 표시한다")
	void balanceComparison() {
		// given
		Payment matched = paymentFixture.createApproved(rental(START));
		Payment mismatched = paymentFixture.createApproved(rental(START.plusDays(5)));
		paymentGateway.changeBalance(mismatched.getOrderId(), mismatched.getAmount() - 1_000);
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then
		assertThat(reload(matched).isNeedsAdminCheck()).isFalse();
		Payment flagged = reload(mismatched);
		assertThat(flagged.isNeedsAdminCheck()).isTrue();
		assertThat(flagged.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("환불이 남아 있는 결제는 재시도 배치 몫이라 불일치로 보지 않는다")
	void pendingRefundNotFlagged() {
		// given
		Payment payment = paymentFixture.createApproved(rental(START));
		payment.requestRefund(payment.getAmount(), CREATED_AT);
		paymentRepository.save(payment);
		paymentGateway.changeBalance(payment.getOrderId(), 0);
		testClock.fixAt(RUN_AT);

		// when
		job.reconcileRecent(RUN_AT);

		// then
		assertThat(reload(payment).isNeedsAdminCheck()).isFalse();
	}

	@Test
	@DisplayName("막 바뀐 결제(payment.expire-minutes 안)와 범위보다 오래된 결제는 조회하지 않는다")
	void window() {
		// given
		paymentFixture.createApproved(rental(START));

		// when: 만든 직후
		testClock.fixAt(CREATED_AT.plusMinutes(14));
		assertThat(job.reconcileRecent(CREATED_AT.plusMinutes(14))).isZero();

		// when: 범위 경계
		testClock.fixAt(CREATED_AT.plusHours(24));
		assertThat(job.reconcileRecent(CREATED_AT.plusHours(24))).isEqualTo(1);

		// when: 범위 밖
		testClock.fixAt(CREATED_AT.plusHours(24).plusSeconds(1));
		assertThat(job.reconcileRecent(CREATED_AT.plusHours(24).plusSeconds(1))).isZero();
	}

	private Rental pendingRental(LocalDateTime expiresAt) {
		return rentalRepository.save(Rental.createPendingPayment(item.getId(), item.getOwnerId(), renter.getId(),
			START, START, 10_000, 50_000, expiresAt));
	}

	private Rental rental(LocalDate start) {
		return rentalFixture.createRental(item, renter, RentalStatus.RESERVED, start, start);
	}

	// 승인 요청을 보내고 응답을 받지 못한 상태: READY + 시도 PENDING
	private Payment unknownApproval(Rental rental) {
		Payment payment = paymentRepository.save(Payment.createForRental(rental.getId(), "order-" + rental.getId(),
			"key-" + rental.getId(), rental.getRentalFee(), rental.getDeposit()));
		transactionRepository.save(PaymentTransaction.pending(payment.getId(), PaymentTransactionAction.APPROVE,
			payment.getAmount(), payment.getIdempotencyKey()));
		return payment;
	}

	private long pendingApprovals() {
		return transactionRepository.findAll().stream()
			.filter(transaction -> transaction.getAction() == PaymentTransactionAction.APPROVE)
			.filter(transaction -> transaction.getResult() == PaymentTransactionResult.PENDING)
			.count();
	}

	private Payment reload(Payment payment) {
		return paymentRepository.findById(payment.getId()).orElseThrow();
	}
}
