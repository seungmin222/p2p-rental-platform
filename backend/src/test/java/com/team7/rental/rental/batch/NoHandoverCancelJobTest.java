package com.team7.rental.rental.batch;

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
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.FakePaymentGateway.Fault;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 인계 없음 자동 취소 배치 (REQ-017). handover.auto-cancel-days = 3 */
class NoHandoverCancelJobTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0);
	private static final LocalDate TODAY = NOW.toLocalDate();

	@Autowired
	private NoHandoverCancelJob job;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 30)).item();
	}

	@Test
	@DisplayName("오늘 > 시작일 + 3일이면 취소·전액 환불하고, 시작일 + 3일 당일은 그대로며, 두 번 돌려도 환불은 한 번이다")
	void boundaryRefundAndIdempotent() {
		// given
		Rental due = rental(RentalStatus.RESERVED, TODAY.minusDays(4));
		Rental notYet = rental(RentalStatus.RESERVED, TODAY.minusDays(3));
		Payment payment = paymentFixture.createApproved(due);

		// when
		int first = job.cancelDue(NOW);
		int second = job.cancelDue(NOW);

		// then
		assertThat(first).isEqualTo(1);
		assertThat(second).isZero();
		Rental canceled = rentalRepository.findById(due.getId()).orElseThrow();
		assertThat(canceled.getStatus()).isEqualTo(RentalStatus.CANCELED);
		assertThat(canceled.getCancelType()).isEqualTo(CancelType.NO_HANDOVER);
		assertThat(canceled.getCancelFee()).isZero();
		assertThat(rentalRepository.findById(notYet.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RESERVED);
		Payment refunded = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(refunded.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(paymentGateway.appliedCancels()).containsExactly(refunded.getAmount());
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getChangedBy()).isNull();
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_NO_HANDOVER_BATCH);
		});
	}

	@Test
	@DisplayName("인계된 대여는 대상이 아니고, 결제 없는 대여는 PG 없이 취소된다")
	void onlyReservedAndNoPaymentNoPg() {
		// given
		Rental renting = rental(RentalStatus.RENTING, TODAY.minusDays(10));
		Rental noPayment = rental(RentalStatus.RESERVED, TODAY.minusDays(9));

		// when
		job.cancelDue(NOW);

		// then
		assertThat(rentalRepository.findById(renting.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RENTING);
		assertThat(rentalRepository.findById(noPayment.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.CANCELED);
		assertThat(paymentGateway.cancelCalls()).isZero();
	}

	@Test
	@DisplayName("환불이 실패해도 대여는 취소되고 환불액이 남아 재시도 대상이 된다")
	void refundFailure() {
		// given
		Rental due = rental(RentalStatus.RESERVED, TODAY.minusDays(4));
		Payment payment = paymentFixture.createApproved(due);
		paymentGateway.failNextCancel(Fault.ERROR);

		// when
		job.cancelDue(NOW);

		// then
		assertThat(rentalRepository.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(RentalStatus.CANCELED);
		Payment failed = paymentRepository.findById(payment.getId()).orElseThrow();
		assertThat(failed.getDepositStatus()).isEqualTo(DepositStatus.REFUND_FAILED);
		assertThat(failed.getRefundPendingAmount()).isEqualTo(failed.getAmount());
	}

	private Rental rental(RentalStatus status, LocalDate start) {
		return rentalFixture.createRental(item, renter, status, start, start.plusDays(1));
	}
}
