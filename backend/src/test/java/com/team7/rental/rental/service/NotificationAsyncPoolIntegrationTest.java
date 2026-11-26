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
import org.springframework.test.context.TestPropertySource;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.notification.repository.NotificationRepository;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 커넥션 풀 교착 회귀 테스트 (docs/experiments/load-test.md "병목"). 예약 커밋 뒤 알림 저장(REQUIRES_NEW)이 요청 스레드에서 돌면 요청 하나가
 * 커넥션 2개를 잡아, 동시 예약 수가 풀 크기를 넘으면 서로 기다리다 connection-timeout으로 실패한다. 알림 리스너를 전용 실행기에서 돌리면 풀이
 * 작아도 모두 성공해야 한다. 풀을 2개로 줄여 그 상황을 만든다(app.notification.executor.async=false로 바꾸면 이 테스트가 실패한다).
 */
@TestPropertySource(properties = {"app.notification.executor.async=true",
	"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=5000"})
class NotificationAsyncPoolIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int REQUESTS = 10;

	@Autowired
	private RentalService rentalService;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	private final List<Item> items = new ArrayList<>();
	private final List<Member> renters = new ArrayList<>();

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		for (int i = 0; i < REQUESTS; i++) {
			items.add(fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 11, 1),
				LocalDate.of(2026, 11, 30)).item());
			renters.add(fixture.createVerifiedMember().member());
		}
	}

	@Test
	@DisplayName("풀이 2개여도 서로 다른 물품 10건을 동시에 예약하면 모두 성공하고 알림(양측 2건씩)도 저장된다")
	void noPoolDeadlock() throws InterruptedException {
		// given
		ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
		CountDownLatch ready = new CountDownLatch(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(REQUESTS);
		AtomicInteger successCount = new AtomicInteger();
		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

		// when
		try {
			for (int i = 0; i < REQUESTS; i++) {
				Long renterId = renters.get(i).getId();
				Long itemId = items.get(i).getId();
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						rentalService.create(renterId, new RentalCreateRequest(itemId, START, END));
						successCount.incrementAndGet();
					} catch (Throwable e) {
						failures.add(e);
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

		// then
		assertThat(failures).isEmpty();
		assertThat(successCount.get()).isEqualTo(REQUESTS);
		assertThat(rentalRepository.count()).isEqualTo(REQUESTS);
		assertThat(awaitNotificationCount(REQUESTS * 2L)).isEqualTo(REQUESTS * 2L);
	}

	/** 알림은 실행기에서 저장되므로 기대 개수가 될 때까지 잠깐 기다린다 */
	private long awaitNotificationCount(long expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		long count = notificationRepository.count();
		while (count < expected && System.nanoTime() < deadline) {
			Thread.sleep(50);
			count = notificationRepository.count();
		}
		return count;
	}
}
