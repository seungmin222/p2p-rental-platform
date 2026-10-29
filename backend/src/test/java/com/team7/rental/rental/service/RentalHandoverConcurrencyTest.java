package com.team7.rental.rental.service;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 인계는 행 락 없이 @Version으로 동시 변경을 막는다(REQ-017). 실제 스레드로 동시에 시작하고, 한 번의 경쟁은 겹치지 않을 수 있어 대여 여러 건에서
 * 반복한다.
 */
class RentalHandoverConcurrencyTest extends IntegrationTestSupport {

	private static final int ROUNDS = 10;
	private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 10, 0);
	private static final List<String> CONFLICT_CODES = List.of("COMMON_CONCURRENT_UPDATE",
		"RENTAL_INVALID_TRANSITION");

	@Autowired
	private RentalInspectionTxService rentalInspectionTxService;

	@Autowired
	private RentalCancelService rentalCancelService;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Member owner;
	private Member renter;
	private List<Rental> rentals;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, PERIOD_START, PERIOD_START.plusDays(29)).item();
		// 모두 시작일이 지났고 인계 전인 대여 예정: [11/1, 11/2], [11/3, 11/4], ...
		rentals = new ArrayList<>();
		for (int i = 0; i < ROUNDS; i++) {
			LocalDate start = PERIOD_START.plusDays(i * 2L);
			rentals.add(rentalFixture.createRental(item, renter, RentalStatus.RESERVED, start, start.plusDays(1)));
		}
	}

	@Test
	@DisplayName("같은 대여에 인계 2건이 동시에 오면 200 1건 + 409 1건이고 500이 없으며 기록·이력·사진은 1건씩이다")
	void concurrentHandover() throws InterruptedException {
		for (Rental rental : rentals) {
			// given
			long storedBefore = storedImageCount();

			// when
			List<Response> responses = runConcurrently(List.of(
				() -> handover(rental),
				() -> handover(rental)));

			// then: 늦은 쪽은 사전 검사에서 INVALID_TRANSITION 또는 버전 충돌로 COMMON_CONCURRENT_UPDATE
			assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
			assertThat(conflictOf(responses).code()).isIn(CONFLICT_CODES);
			assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
				.isEqualTo(RentalStatus.RENTING);
			assertThat(inspectionRepository.findAllByRentalIdOrderById(rental.getId())).singleElement()
				.extracting(inspection -> inspection.getPhase())
				.isEqualTo(InspectionPhase.HANDOVER);
			assertThat(historiesOf(rental)).singleElement()
				.extracting(RentalStatusHistory::getReason)
				.isEqualTo(RentalStatusHistory.REASON_HANDOVER);
			// 진 쪽이 업로드했다면 지웠다
			assertThat(storedImageCount()).isEqualTo(storedBefore + 1);
		}
	}

	@Test
	@DisplayName("인계와 양측 동의 취소 수락이 동시에 오면 한쪽만 성공하고 상태·기록·이력이 이긴 쪽과 맞는다")
	void handoverAndCancelAgreementAccept() throws InterruptedException {
		// 단독 취소는 시작일 전, 인계는 시작일부터라 같은 시각에 둘 다 가능한 경우가 없다. 시작일 이후 취소인 동의 취소 수락과 경쟁시킨다
		for (Rental rental : rentals) {
			// given: 제공자가 동의 취소를 요청했고, 대여자 수락과 제공자 인계가 동시에
			Long agreementId = rentalCancelService.requestAgreement(rental.getId(), owner.getId(),
				new RentalCancelRequest(CancelReason.SCHEDULE_CHANGE, null)).agreementId();

			// when
			List<Response> responses = runConcurrently(List.of(
				() -> handover(rental),
				() -> acceptAgreement(rental, agreementId)));

			// then
			assertThat(responses).extracting(Response::status).containsExactlyInAnyOrder(200, 409);
			assertThat(conflictOf(responses).code()).isIn(CONFLICT_CODES);
			RentalStatus status = rentalRepository.findById(rental.getId()).orElseThrow().getStatus();
			List<RentalStatusHistory> histories = historiesOf(rental);
			if (status == RentalStatus.RENTING) {
				assertThat(inspectionRepository.findAllByRentalIdOrderById(rental.getId())).hasSize(1);
				assertThat(histories).singleElement()
					.extracting(RentalStatusHistory::getToStatus)
					.isEqualTo(RentalStatus.RENTING);
			} else {
				assertThat(status).isEqualTo(RentalStatus.CANCELED);
				assertThat(inspectionRepository.findAllByRentalIdOrderById(rental.getId())).isEmpty();
				assertThat(histories).singleElement()
					.extracting(RentalStatusHistory::getToStatus)
					.isEqualTo(RentalStatus.CANCELED);
			}
		}
	}

	@Test
	@DisplayName("두 트랜잭션이 같은 버전을 읽고 동시에 인계하면 늦은 쪽은 기록 INSERT 전에 버전 충돌로 끝난다(UK 위반 아님)")
	void staleReadHandoverFailsOnVersion() throws InterruptedException {
		// 사전 검사를 건너뛰고 TxService를 직접 부른다. 둘 다 대여를 읽은 뒤 출발해 버전 충돌 구간을 넓힌다.
		// rental flush를 빼면 늦은 쪽이 UK 대기 중 데드락(CannotAcquireLockException)으로 끝난다(notes.md에 확인 결과)
		for (Rental rental : rentals) {
			// given
			CyclicBarrier readDone = new CyclicBarrier(2);
			Callable<Object> staleHandover = () -> transactionTemplate.execute(status -> {
				rentalRepository.findById(rental.getId()).orElseThrow();
				await(readDone);
				return rentalInspectionTxService.handover(rental.getId(), owner.getId(), null,
					List.of("/files/inspections/test.jpg"));
			});

			// when
			List<Throwable> failures = runConcurrentlyForFailures(List.of(staleHandover, staleHandover));

			// then
			assertThat(failures).singleElement().isInstanceOf(ObjectOptimisticLockingFailureException.class);
			assertThat(inspectionRepository.findAllByRentalIdOrderById(rental.getId())).hasSize(1);
			assertThat(historiesOf(rental)).hasSize(1);
		}
	}

	private Response handover(Rental rental) throws Exception {
		return toResponse(mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover",
			rental.getId())
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andReturn());
	}

	private Response acceptAgreement(Rental rental, Long agreementId) throws Exception {
		return toResponse(mockMvc.perform(patch("/api/rentals/{rentalId}/cancel-agreements/{agreementId}",
			rental.getId(), agreementId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"action\": \"ACCEPT\"}")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andReturn());
	}

	private Response toResponse(MvcResult result) throws Exception {
		int status = result.getResponse().getStatus();
		String code = status == 200 ? null
			: JsonPath.read(result.getResponse().getContentAsString(), "$.error.code");
		return new Response(status, code);
	}

	private Response conflictOf(List<Response> responses) {
		return responses.stream().filter(response -> response.status() == 409).findFirst().orElseThrow();
	}

	private List<RentalStatusHistory> historiesOf(Rental rental) {
		return historyRepository.findAll().stream()
			.filter(history -> history.getRentalId().equals(rental.getId()))
			.toList();
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private List<Response> runConcurrently(List<Callable<Response>> tasks) throws InterruptedException {
		ConcurrentLinkedQueue<Response> responses = new ConcurrentLinkedQueue<>();
		List<Throwable> failures = runConcurrentlyForFailures(tasks.stream()
			.<Callable<Object>>map(task -> () -> responses.add(task.call()))
			.toList());
		assertThat(failures).isEmpty();
		return List.copyOf(responses);
	}

	/** 실패한 작업의 예외 목록. 성공은 빠진다 */
	private List<Throwable> runConcurrentlyForFailures(List<Callable<Object>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(tasks.size());
		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
		try {
			for (Callable<Object> task : tasks) {
				executor.submit(() -> {
					ready.countDown();
					try {
						start.await();
						task.call();
					} catch (Throwable e) {
						failures.add(e);
					} finally {
						done.countDown();
					}
				});
			}
			ready.await();
			start.countDown();
			assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		return List.copyOf(failures);
	}

	private record Response(int status, String code) {
	}
}
