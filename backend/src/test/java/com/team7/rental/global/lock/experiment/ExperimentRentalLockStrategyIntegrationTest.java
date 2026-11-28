package com.team7.rental.global.lock.experiment;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalService;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 동시성 실험 전략의 전제 확인(docs/experiments/concurrency.md). ① 바깥 트랜잭션의 격리 수준이 READ_COMMITTED로 고정된 안쪽
 * 트랜잭션 메서드에도 적용된다(합류). ② 비관적 락(물품 행 FOR UPDATE)으로 같은 기간 동시 예약이 1건만 성공한다.
 */
@TestPropertySource(properties = {"app.experiment.rental-lock.mode=PESSIMISTIC_ITEM",
	"app.experiment.rental-lock.isolation=REPEATABLE_READ"})
@Import(ExperimentRentalLockStrategyIntegrationTest.IsolationProbeConfig.class)
class ExperimentRentalLockStrategyIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final int REQUESTS = 10;

	@Autowired
	private RentalLockStrategy rentalLockStrategy;

	@Autowired
	private IsolationProbe isolationProbe;

	@Autowired
	private RentalService rentalService;

	@Autowired
	private RentalRepository rentalRepository;

	private Item item;
	private final List<Member> renters = new ArrayList<>();

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		item = fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30)).item();
		for (int i = 0; i < REQUESTS; i++) {
			renters.add(fixture.createVerifiedMember().member());
		}
	}

	@Test
	@DisplayName("실험 모드에서는 실험 전략이 기본 전략을 대신한다")
	void experimentStrategyIsPrimary() {
		assertThat(rentalLockStrategy).isInstanceOf(ExperimentRentalLockStrategy.class);
	}

	@Test
	@DisplayName("READ_COMMITTED로 고정된 안쪽 트랜잭션도 바깥 트랜잭션의 REPEATABLE READ로 실행된다")
	void innerTransactionJoinsOuterIsolation() {
		// when
		String isolation = rentalLockStrategy.executeWithItemLock(item.getId(), isolationProbe::readCommittedMethod);

		// then
		assertThat(isolation).isEqualTo("REPEATABLE-READ");
	}

	@Test
	@DisplayName("물품 행 비관적 락으로 같은 기간 동시 예약 10건 중 1건만 성공하고 나머지는 기간 충돌이다")
	void pessimisticItemLockAllowsOnlyOne() throws InterruptedException {
		// given
		ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
		CountDownLatch ready = new CountDownLatch(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(REQUESTS);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger conflictCount = new AtomicInteger();
		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

		// when
		try {
			for (Member renter : renters) {
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						rentalService.create(renter.getId(), new RentalCreateRequest(item.getId(), START, END));
						successCount.incrementAndGet();
					} catch (BusinessException e) {
						if (e.getErrorCode() == ErrorCode.RENTAL_PERIOD_CONFLICT) {
							conflictCount.incrementAndGet();
						} else {
							failures.add(e);
						}
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
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(conflictCount.get()).isEqualTo(REQUESTS - 1);
		assertThat(rentalRepository.count()).isEqualTo(1);
	}

	@TestConfiguration
	static class IsolationProbeConfig {

		@Bean
		IsolationProbe isolationProbe(JdbcTemplate jdbcTemplate) {
			return new IsolationProbe(jdbcTemplate);
		}
	}

	/** RentalReservationTxService.create처럼 READ_COMMITTED가 고정된 트랜잭션 메서드 */
	static class IsolationProbe {

		private final JdbcTemplate jdbcTemplate;

		IsolationProbe(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@Transactional(isolation = Isolation.READ_COMMITTED)
		public String readCommittedMethod() {
			return jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class);
		}
	}
}
