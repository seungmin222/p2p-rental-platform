package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * 트랜잭션이 watchdog 주기보다 길어도 락이 커밋 전에 풀리지 않는지(REQ-014).
 * <p>
 * watchdog을 0.5초로 줄이고, INSERT 뒤 커밋 전에 1.5초 잠드는 트랜잭션 빈을 끼운다. A가 잠든 동안 B가 같은 기간을 예약하면
 * B는 락을 기다렸다가 A의 커밋을 보고 RENTAL_PERIOD_CONFLICT여야 한다. 임대 시간을 지정한 구현이라면 0.5초 뒤 락이 풀려 B가
 * 커밋 전의 A를 보지 못하고 중복 예약이 생긴다. 지연(1.5초)은 watchdog(0.5초)보다 충분히 길고 lock.wait(3초)보다 충분히 짧다.
 * <p>
 * 설정을 바꾸므로 스프링 컨텍스트가 따로 뜬다.
 */
@Import(RentalLockReleaseTest.SlowTransactionConfig.class)
class RentalLockReleaseTest extends IntegrationTestSupport {

	private static final long WATCHDOG_TIMEOUT_MILLIS = 500;
	private static final long TRANSACTION_DELAY_MILLIS = 1_500;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private RentalService rentalService;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private SlowRentalReservationTxService slowTxService;

	@Autowired
	private PolicyProperties policyProperties;

	private Item item;
	private Member renterA;
	private Member renterB;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		item = fixture.createItem(fixture.createVerifiedMember(), LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 30)).item();
		renterA = fixture.createVerifiedMember().member();
		renterB = fixture.createVerifiedMember().member();
		slowTxService.reset();
	}

	@Test
	@DisplayName("트랜잭션이 watchdog 주기보다 길어도 커밋 전에 락이 풀리지 않아, 뒤 요청은 RENTAL_PERIOD_CONFLICT다")
	void lockHeldUntilCommit() throws Exception {
		// given: 이 테스트의 전제(watchdog < 지연 < 락 대기)
		assertThat(redissonClient.getConfig().getLockWatchdogTimeout()).isEqualTo(WATCHDOG_TIMEOUT_MILLIS);
		assertThat(policyProperties.rental().lockWait().toMillis())
			.isGreaterThanOrEqualTo(TRANSACTION_DELAY_MILLIS * 2);

		// when: A가 INSERT 뒤 잠든 동안 B가 같은 기간을 예약
		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> reserve(renterA));
		assertThat(slowTxService.awaitSleeping()).isTrue();
		Throwable second = catchThrowable(() -> reserve(renterB));
		first.get(10, TimeUnit.SECONDS);

		// then
		assertThat(slowTxService.isSleptInTransaction()).isTrue();
		assertThat(second).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.RENTAL_PERIOD_CONFLICT);
		assertThat(rentalRepository.count()).isEqualTo(1);
	}

	private void reserve(Member renter) {
		rentalService.create(renter.getId(), new RentalCreateRequest(item.getId(), START, END));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SlowTransactionConfig {

		@Bean
		RedissonAutoConfigurationCustomizer shortWatchdogCustomizer() {
			return config -> config.setLockWatchdogTimeout(WATCHDOG_TIMEOUT_MILLIS);
		}

		@Bean
		@Primary
		SlowRentalReservationTxService slowRentalReservationTxService(RentalRepository rentalRepository,
			RentalStatusHistoryRepository rentalStatusHistoryRepository, RentalOccupancyReader rentalOccupancyReader,
			PolicyProperties policyProperties, Clock clock,
			@Value("${app.feature.payment-enabled}")
			boolean paymentEnabled) {
			return new SlowRentalReservationTxService(rentalRepository, rentalStatusHistoryRepository,
				rentalOccupancyReader, policyProperties, clock, paymentEnabled);
		}
	}

	/** INSERT가 끝난 뒤 커밋 전에 잠든다. 겹침으로 실패한 호출은 잠들지 않는다 */
	static class SlowRentalReservationTxService extends RentalReservationTxService {

		private final AtomicBoolean sleptInTransaction = new AtomicBoolean();
		private volatile CountDownLatch sleeping = new CountDownLatch(1);

		SlowRentalReservationTxService(RentalRepository rentalRepository,
			RentalStatusHistoryRepository rentalStatusHistoryRepository, RentalOccupancyReader rentalOccupancyReader,
			PolicyProperties policyProperties, Clock clock, boolean paymentEnabled) {
			super(rentalRepository, rentalStatusHistoryRepository, rentalOccupancyReader, policyProperties, clock,
				paymentEnabled);
		}

		// 상속된 @Transactional에 기대지 않고 다시 선언한다
		@Override
		@Transactional(isolation = Isolation.READ_COMMITTED)
		public Rental create(Long renterId, ItemRentalTarget item, LocalDate startDate, LocalDate endDate) {
			Rental rental = super.create(renterId, item, startDate, endDate);
			sleptInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
			sleeping.countDown();
			try {
				Thread.sleep(TRANSACTION_DELAY_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			return rental;
		}

		void reset() {
			sleptInTransaction.set(false);
			sleeping = new CountDownLatch(1);
		}

		boolean awaitSleeping() throws InterruptedException {
			return sleeping.await(5, TimeUnit.SECONDS);
		}

		boolean isSleptInTransaction() {
			return sleptInTransaction.get();
		}
	}
}
