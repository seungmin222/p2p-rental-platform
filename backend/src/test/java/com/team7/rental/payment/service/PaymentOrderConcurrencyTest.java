package com.team7.rental.payment.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 서로 다른 대여의 주문 생성이 MySQL 갭 락으로 교착되지 않는다 (REQ-024) */
class PaymentOrderConcurrencyTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final int ROUNDS = 30;

	@Autowired
	private PaymentTxService paymentTxService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Test
	@DisplayName("서로 다른 두 대여의 주문을 동시에 만들어도 교착 없이 모두 성공한다")
	void twoRentalsNoDeadlock() throws Exception {
		// given
		testClock.fixAt(NOW);
		VerifiedMember owner = fixture.createVerifiedMember();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, START, START.plusDays(ROUNDS * 2)).item();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			for (int round = 0; round < ROUNDS; round++) {
				Rental first = pending(item, renter, round * 2);
				Rental second = pending(item, renter, round * 2 + 1);
				CountDownLatch start = new CountDownLatch(1);
				List<Future<?>> futures = new ArrayList<>();
				for (Rental rental : List.of(first, second)) {
					futures.add(executor.submit(() -> {
						start.await();
						return paymentTxService.createOrder(rental.getId(), renter.getId());
					}));
				}

				// when
				start.countDown();

				// then: 교착이면 한쪽이 CannotAcquireLockException으로 실패한다
				for (Future<?> future : futures) {
					assertThatCode(future::get).doesNotThrowAnyException();
				}
			}
		}
		assertThat(paymentRepository.count()).isEqualTo(ROUNDS * 2L);
	}

	private Rental pending(Item item, Member renter, int offset) {
		LocalDate day = START.plusDays(offset);
		return rentalFixture.createPendingPayment(item, renter, day, day, NOW.plusMinutes(15));
	}
}
