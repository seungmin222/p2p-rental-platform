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
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.PaymentTransactionRepository;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 결제 만료 배치 (REQ-024). payment.expire-minutes = 15 */
class PaymentExpireJobTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);

	@Autowired
	private PaymentExpireJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentTransactionRepository transactionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("만료 시각이 된 대여만 CANCELED(PAYMENT_EXPIRED)가 되고 이력은 시스템이며, 두 번 돌려도 같다")
	void boundaryAndIdempotent() {
		// given
		Rental due = rentalFixture.createPendingPayment(item, renter, START, START, NOW);
		Rental notYet = rentalFixture.createPendingPayment(item, renter, START.plusDays(5), START.plusDays(5),
			NOW.plusSeconds(1));

		// when
		int first = job.expireDue(NOW);
		int second = job.expireDue(NOW);

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		Rental expired = rentalRepository.findById(due.getId()).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(expired.getCancelType()).isEqualTo(CancelType.PAYMENT_EXPIRED);
		assertThat(expired.getCanceledAt()).isEqualTo(NOW);
		assertThat(rentalRepository.findById(notYet.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.PENDING_PAYMENT);
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_PAYMENT_EXPIRED);
		});
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("결제창만 열고 끝난 READY 주문은 FAILED가 된다")
	void readyOrderFails() {
		// given
		Rental due = rentalFixture.createPendingPayment(item, renter, START, START, NOW);
		Payment payment = paymentRepository.save(order(due, "order-1"));

		// when
		job.expireDue(NOW);

		// then
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.FAILED);
	}

	@Test
	@DisplayName("승인 결과를 모르는(시도 PENDING) READY 주문은 조정 배치가 보도록 남겨 둔다")
	void unknownApprovalStaysReady() {
		// given
		Rental due = rentalFixture.createPendingPayment(item, renter, START, START, NOW);
		Payment payment = paymentRepository.save(order(due, "order-1"));
		transactionRepository.save(PaymentTransaction.pending(payment.getId(), PaymentTransactionAction.APPROVE,
			payment.getAmount(), payment.getIdempotencyKey()));

		// when
		job.expireDue(NOW);

		// then
		assertThat(rentalRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.READY);
	}

	private Payment order(Rental rental, String orderId) {
		return Payment.createForRental(rental.getId(), orderId, "key-" + orderId, rental.getRentalFee(),
			rental.getDeposit());
	}
}
