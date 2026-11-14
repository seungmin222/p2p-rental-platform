package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.dto.ItemPeriodRequest;
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 물품 삭제(REQ-008)·가능 기간 수정(REQ-007)과 예약(REQ-013)이 같은 물품에 동시에 들어올 때 정합성(open-issues #27).
 * 순서는 매번 달라질 수 있으므로 여러 번 반복하고, 어느 쪽이 먼저든 둘 중 하나의 일관된 결과만 남는지 본다.
 */
class ItemEditDeleteConcurrencyTest extends IntegrationTestSupport {

	private static final int ROUNDS = 10;
	private static final LocalDate RENTAL_START = LocalDate.of(2026, 11, 10);
	private static final LocalDate RENTAL_END = LocalDate.of(2026, 11, 12);

	@Autowired
	private ItemService itemService;
	@Autowired
	private RentalService rentalService;
	@Autowired
	private ItemRepository itemRepository;
	@Autowired
	private ItemAvailablePeriodRepository periodRepository;
	@Autowired
	private RentalRepository rentalRepository;

	private VerifiedMember owner;
	private Member renter;

	@BeforeEach
	void setUp() {
		testClock.fixAt(LocalDateTime.of(2026, 11, 2, 12, 0));
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
	}

	@Test
	@DisplayName("삭제와 예약이 동시에 오면 '삭제됨 + 대여 없음' 또는 '대여 있음 + 삭제 409' 중 하나만 남는다")
	void deleteAndReserve() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Item item = newItem();

			Outcome<Void> deletion = new Outcome<>();
			Outcome<Object> reservation = new Outcome<>();
			runTogether(() -> deletion.run(() -> {
				itemService.delete(item.getId(), owner.member().getId());
				return null;
			}), () -> reservation.run(() -> reserve(item)));

			boolean isDeleted = itemRepository.findById(item.getId()).orElseThrow().getStatus() == ItemStatus.DELETED;
			long rentals = countRentals(item);
			assertThat(deletion.unexpected).isNull();
			assertThat(reservation.unexpected).isNull();
			if (isDeleted) {
				assertThat(rentals).isZero();
				assertThat(reservation.errorCode).isIn(ErrorCode.ITEM_NOT_FOUND, ErrorCode.RENTAL_LOCK_TIMEOUT);
			} else {
				assertThat(rentals).isEqualTo(1);
				assertThat(deletion.errorCode).isIn(ErrorCode.ITEM_HAS_ACTIVE_RENTAL, ErrorCode.RENTAL_LOCK_TIMEOUT);
			}
		}
	}

	@Test
	@DisplayName("예약 날짜를 빼는 기간 수정과 예약이 동시에 오면 '수정됨 + 대여 없음' 또는 '대여 있음 + 수정 409' 중 하나만 남는다")
	void shrinkPeriodAndReserve() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			Item item = newItem();
			ItemUpdateRequest shrink = new ItemUpdateRequest(null, null, null, null, null, null, null,
				List.of(new ItemPeriodRequest(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5))), null, null,
				null, null);

			Outcome<Object> update = new Outcome<>();
			Outcome<Object> reservation = new Outcome<>();
			runTogether(() -> update.run(() -> itemService.update(item.getId(), owner.member().getId(), shrink,
				null)), () -> reservation.run(() -> reserve(item)));

			boolean isShrunk = periodRepository.findAllByItemIdOrderByStartDate(item.getId()).getFirst()
				.getEndDate().equals(LocalDate.of(2026, 11, 5));
			long rentals = countRentals(item);
			assertThat(update.unexpected).isNull();
			assertThat(reservation.unexpected).isNull();
			if (isShrunk) {
				assertThat(rentals).isZero();
				assertThat(reservation.errorCode).isIn(ErrorCode.RENTAL_OUT_OF_AVAILABLE_PERIOD,
					ErrorCode.RENTAL_LOCK_TIMEOUT);
			} else {
				assertThat(rentals).isEqualTo(1);
				assertThat(update.errorCode).isIn(ErrorCode.ITEM_PERIOD_CONFLICT, ErrorCode.RENTAL_LOCK_TIMEOUT);
			}
		}
	}

	private Item newItem() {
		return fixture.createItem(owner, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30)).item();
	}

	private Object reserve(Item item) {
		return rentalService.create(renter.getId(), new RentalCreateRequest(item.getId(), RENTAL_START, RENTAL_END));
	}

	private long countRentals(Item item) {
		return rentalRepository.findAll().stream().filter(rental -> rental.getItemId().equals(item.getId())).count();
	}

	private void runTogether(Runnable first, Runnable second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = List.of(executor.submit(gate(first, ready, start)),
				executor.submit(gate(second, ready, start)));
			ready.await();
			start.countDown();
			for (Future<?> future : futures) {
				future.get(60, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static Callable<Void> gate(Runnable task, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			start.await();
			task.run();
			return null;
		};
	}

	/** 한 요청의 결과: 성공이면 둘 다 null, 업무 예외면 errorCode, 그 밖의 예외면 unexpected */
	private static final class Outcome<T> {
		private volatile ErrorCode errorCode;
		private volatile Throwable unexpected;

		void run(Callable<T> task) {
			try {
				task.call();
			} catch (BusinessException e) {
				errorCode = e.getErrorCode();
			} catch (Throwable e) {
				unexpected = e;
			}
		}
	}
}
