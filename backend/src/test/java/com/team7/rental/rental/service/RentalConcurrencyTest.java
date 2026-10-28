package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 한 JVM의 여러 스레드가 같은 물품을 동시에 예약한다(REQ-014). 2개 인스턴스 검증은 락 실험 브랜치에서 한다.
 */
class RentalConcurrencyTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private RentalService rentalService;

	@Autowired
	private RentalRepository rentalRepository;

	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		item = fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("100명이 같은 물품·기간을 동시에 예약하면 1건만 성공하고 나머지는 409, DB에도 1건이다")
	void samePeriod() throws InterruptedException {
		// given
		List<Request> requests = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			requests.add(new Request(fixture.createVerifiedMember().member(), START, END));
		}

		// when
		Result result = runConcurrently(requests);

		// then
		assertThat(result.successCount()).isEqualTo(1);
		assertThat(result.errors()).hasSize(99)
			.allSatisfy(code -> assertThat(code).isIn(ErrorCode.RENTAL_PERIOD_CONFLICT, ErrorCode.RENTAL_LOCK_TIMEOUT));
		assertThat(result.unexpected()).isEmpty();
		assertThat(rentalRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("서로 겹치지 않는 기간은 동시에 예약해도 모두 성공한다")
	void disjointPeriods() throws InterruptedException {
		// given: [11/1, 11/2], [11/3, 11/4], ... [11/19, 11/20]
		List<Request> requests = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			LocalDate start = LocalDate.of(2026, 11, 1).plusDays(i * 2L);
			requests.add(new Request(fixture.createVerifiedMember().member(), start, start.plusDays(1)));
		}

		// when
		Result result = runConcurrently(requests);

		// then
		assertThat(result.successCount()).isEqualTo(10);
		assertThat(result.errors()).isEmpty();
		assertThat(result.unexpected()).isEmpty();
		assertThat(rentalRepository.count()).isEqualTo(10);
	}

	private Result runConcurrently(List<Request> requests) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(requests.size());
		CountDownLatch ready = new CountDownLatch(requests.size());
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(requests.size());
		AtomicInteger successCount = new AtomicInteger();
		ConcurrentLinkedQueue<ErrorCode> errors = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
		try {
			for (Request request : requests) {
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						rentalService.create(request.renter().getId(),
							new RentalCreateRequest(item.getId(), request.start(), request.end()));
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
		return new Result(successCount.get(), List.copyOf(errors), List.copyOf(unexpected));
	}

	private record Request(Member renter, LocalDate start, LocalDate end) {
	}

	private record Result(int successCount, List<ErrorCode> errors, List<Throwable> unexpected) {
	}
}
