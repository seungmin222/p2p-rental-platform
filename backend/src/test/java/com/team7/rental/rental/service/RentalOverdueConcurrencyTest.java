package com.team7.rental.rental.service;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.batch.OverdueDetectJob;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 연체 배치와 반납 요청은 같은 대여 행 락으로 한 줄로 선다(REQ-023). 반납 요청은 항상 성공하고, 연체료는 날짜로 정해지며, 배치는 RETURNED를 건너뛴다.
 * 일 대여료 10,000원(연체 1일 15,000원), 보증금 50,000원, 예정일 11-20, 오늘 11-22 → 연체 2일 30,000원.
 */
class RentalOverdueConcurrencyTest extends IntegrationTestSupport {

	private static final int ROUNDS = 10;
	private static final LocalDate START = LocalDate.of(2026, 11, 18);
	private static final LocalDate END = LocalDate.of(2026, 11, 20);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 0, 5);
	private static final int EXPECTED_FEE = 30_000;
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";

	@Autowired
	private OverdueDetectJob overdueDetectJob;

	@Autowired
	private RentalOverdueTxService rentalOverdueTxService;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

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
	@DisplayName("배치와 반납 요청을 동시에 돌리면 반납은 항상 200이고 연체료 30,000원이 확정되며, 이력·알림은 이긴 순서와 맞는다")
	void concurrentBatchAndReturn() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			// given: 라운드마다 새 대여 (이전 라운드 건은 RETURNED라 배치 대상이 아니다)
			notificationPort.reset();
			Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);

			// when
			Future<Integer> batch;
			Future<MvcResult> returned;
			try {
				batch = executor.submit(() -> {
					start.await();
					return overdueDetectJob.markDue(NOW);
				});
				returned = executor.submit(() -> {
					start.await();
					return requestReturn(rental);
				});
				start.countDown();
				// then
				assertThat(returned.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
				batch.get(30, TimeUnit.SECONDS);
			} finally {
				executor.shutdownNow();
			}

			assertReturnedWithFee(rental);
			List<RentalStatusHistory> histories = historiesOf(rental);
			if (histories.size() == 2) {
				// 배치가 먼저: 반납 요청은 배치 커밋을 기다린 뒤 OVERDUE에서 반납했다
				assertThat(histories).extracting(RentalStatusHistory::getToStatus)
					.containsExactly(RentalStatus.OVERDUE, RentalStatus.RETURNED);
				assertThat(histories.get(1).getFromStatus()).isEqualTo(RentalStatus.OVERDUE);
				assertThat(overdueNotifications()).hasSize(2)
					.extracting(Sent::eventId).containsOnly("OVERDUE:" + rental.getId());
			} else {
				// 반납이 먼저: 배치는 RETURNED를 보고 건너뛰었다
				assertThat(histories).singleElement().satisfies(history -> {
					assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RENTING);
					assertThat(history.getToStatus()).isEqualTo(RentalStatus.RETURNED);
				});
				assertThat(overdueNotifications()).isEmpty();
			}
		}
	}

	@Test
	@DisplayName("배치가 행 락을 쥐고 있으면 반납 요청은 커밋을 기다린 뒤 OVERDUE에서 200으로 반납된다")
	void returnWaitsForBatch() throws Exception {
		// given
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> batch = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				assertThat(rentalOverdueTxService.markOverdue(rental.getId(), NOW)).isPresent();
				locked.countDown();
				await(release);
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			// when: 배치 트랜잭션이 열린 채로 반납 요청
			Future<MvcResult> returned = executor.submit(() -> requestReturn(rental));
			// 반납 요청이 락에서 기다리는지: 아직 끝나지 않았다
			Thread.sleep(500);
			assertThat(returned.isDone()).isFalse();
			release.countDown();
			batch.get(10, TimeUnit.SECONDS);

			// then
			assertThat(returned.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
		} finally {
			executor.shutdownNow();
		}
		assertReturnedWithFee(rental);
		assertThat(historiesOf(rental)).extracting(RentalStatusHistory::getToStatus)
			.containsExactly(RentalStatus.OVERDUE, RentalStatus.RETURNED);
	}

	@Test
	@DisplayName("반납이 먼저 커밋되면 배치는 RETURNED를 건너뛰고 이력·알림을 남기지 않는다")
	void batchSkipsReturned() throws Exception {
		// given
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		assertThat(requestReturn(rental).getResponse().getStatus()).isEqualTo(200);

		// when
		int marked = overdueDetectJob.markDue(NOW);

		// then
		assertThat(marked).isZero();
		assertThat(rentalOverdueTxService.markOverdue(rental.getId(), NOW)).isEmpty();
		assertReturnedWithFee(rental);
		assertThat(historiesOf(rental)).singleElement()
			.extracting(RentalStatusHistory::getToStatus).isEqualTo(RentalStatus.RETURNED);
		assertThat(overdueNotifications()).isEmpty();
	}

	private MvcResult requestReturn(Rental rental) throws Exception {
		MvcResult result = mockMvc.perform(multipart("/api/rentals/{rentalId}/return-request", rental.getId())
			.file(json(ALL_TRUE))
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))).andReturn();
		if (result.getResponse().getStatus() == 200) {
			Integer fee = JsonPath.read(result.getResponse().getContentAsString(), "$.data.overdueFee");
			assertThat(fee).isEqualTo(EXPECTED_FEE);
		}
		return result;
	}

	private void assertReturnedWithFee(Rental rental) {
		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(RentalStatus.RETURNED);
		assertThat(found.getOverdueFee()).isEqualTo(EXPECTED_FEE);
	}

	private List<RentalStatusHistory> historiesOf(Rental rental) {
		return historyRepository.findAll().stream()
			.filter(history -> history.getRentalId().equals(rental.getId()))
			.sorted((a, b) -> a.getId().compareTo(b.getId()))
			.toList();
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("latch timeout");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	// 반납 요청 알림(REQ-032)도 같은 포트로 가므로 연체 알림만 본다
	private List<Sent> overdueNotifications() {
		return notificationPort.sent().stream()
			.filter(sent -> sent.type() == com.team7.rental.rental.service.RentalNotificationPort.Type.OVERDUE)
			.toList();
	}
}
