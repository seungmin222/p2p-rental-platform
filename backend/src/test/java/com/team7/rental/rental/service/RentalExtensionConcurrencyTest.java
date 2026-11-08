package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.dto.RentalExtensionRequest;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 연장과 예약이 같은 물품 락(lock:item:{itemId})으로 직렬화되는지 (REQ-038, REQ-014). 결제 기능이 꺼진 기본 설정이라 연장은 바로 확정된다.
 * 대여 11/3 ~ 11/5 RENTING을 11/8까지 늘리는 연장 1건과, 11/7 ~ 11/9를 노리는 예약 20건이 동시에 출발한다.
 */
class RentalExtensionConcurrencyTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 20, 0);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDate NEW_END = LocalDate.of(2026, 11, 8);
	private static final int RESERVATIONS = 20;

	@Autowired
	private RentalService rentalService;

	@Autowired
	private RentalExtensionService rentalExtensionService;

	@Autowired
	private RentalRepository rentalRepository;

	private Item item;
	private Member renter;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		item = fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30)).item();
		renter = fixture.createVerifiedMember().member();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, LocalDate.of(2026, 11, 3), END);
	}

	@RepeatedTest(3)
	@DisplayName("연장 1건과 겹치는 예약 20건이 동시에 오면 딱 1건만 성공하고, DB 상태도 그 1건과 맞는다")
	void extensionAndReservationsSerialize() throws InterruptedException {
		// given
		List<Callable<Void>> tasks = new ArrayList<>();
		AtomicBoolean extended = new AtomicBoolean();
		tasks.add(() -> {
			rentalExtensionService.request(renter.getId(), rental.getId(), new RentalExtensionRequest(NEW_END));
			extended.set(true);
			return null;
		});
		for (int i = 0; i < RESERVATIONS; i++) {
			Member other = fixture.createVerifiedMember().member();
			tasks.add(() -> {
				rentalService.create(other.getId(),
					new RentalCreateRequest(item.getId(), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9)));
				return null;
			});
		}

		// when
		Outcome outcome = runConcurrently(tasks);

		// then
		assertThat(outcome.unexpected()).isEmpty();
		assertThat(outcome.successCount()).isEqualTo(1);
		assertThat(outcome.errors()).hasSize(RESERVATIONS)
			.allSatisfy(code -> assertThat(code).isIn(ErrorCode.RENTAL_PERIOD_CONFLICT,
				ErrorCode.RENTAL_EXTENSION_PERIOD_CONFLICT, ErrorCode.RENTAL_LOCK_TIMEOUT));
		Rental after = rentalRepository.findById(rental.getId()).orElseThrow();
		if (extended.get()) {
			assertThat(after.getEndDate()).isEqualTo(NEW_END);
			assertThat(rentalRepository.count()).isEqualTo(1);
		} else {
			assertThat(after.getEndDate()).isEqualTo(END);
			assertThat(rentalRepository.count()).isEqualTo(2);
		}
	}

	private Outcome runConcurrently(List<Callable<Void>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(tasks.size());
		AtomicInteger successCount = new AtomicInteger();
		ConcurrentLinkedQueue<ErrorCode> errors = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
		try {
			for (Callable<Void> task : tasks) {
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						task.call();
						successCount.incrementAndGet();
					} catch (BusinessException e) {
						errors.add(e.getErrorCode());
					} catch (Throwable e) {
						unexpected.add(e);
					} finally {
						done.countDown();
					}
				});
			}
			ready.await();
			start.countDown();
			assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
		} finally {
			executor.shutdownNow();
		}
		return new Outcome(successCount.get(), List.copyOf(errors), List.copyOf(unexpected));
	}

	private record Outcome(int successCount, List<ErrorCode> errors, List<Throwable> unexpected) {
	}
}
