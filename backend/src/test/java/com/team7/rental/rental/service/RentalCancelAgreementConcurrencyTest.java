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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.dto.CancelAgreementAction;
import com.team7.rental.rental.dto.CancelAgreementRespondRequest;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 같은 대여의 동의 취소 요청·응답이 대여 행 락으로 직렬화되는지(REQ-015). 실제 스레드로 동시에 시작한다.
 * 한 번의 경쟁은 겹치지 않을 수도 있어 대여 여러 건에서 반복한다.
 */
class RentalCancelAgreementConcurrencyTest extends IntegrationTestSupport {

	private static final int ROUNDS = 10;
	private static final LocalDate PERIOD_START = LocalDate.of(2026, 11, 1);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 10, 0);
	private static final RentalCancelRequest REQUEST = new RentalCancelRequest(CancelReason.SCHEDULE_CHANGE, null);

	@Autowired
	private RentalCancelService rentalCancelService;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalCancelAgreementRepository agreementRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

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
	@DisplayName("같은 대여에 동의 요청 2건이 동시에 오면 1건만 201이고 나머지는 IN_PROGRESS, DB의 REQUESTED도 1건이다")
	void concurrentRequests() throws InterruptedException {
		for (Rental rental : rentals) {
			// when: 대여자·제공자가 동시에 요청
			Result result = runConcurrently(List.of(
				() -> rentalCancelService.requestAgreement(rental.getId(), renter.getId(), REQUEST),
				() -> rentalCancelService.requestAgreement(rental.getId(), owner.getId(), REQUEST)));

			// then
			assertThat(result.unexpected()).isEmpty();
			assertThat(result.successCount()).isEqualTo(1);
			assertThat(result.errors()).containsExactly(ErrorCode.RENTAL_CANCEL_AGREEMENT_IN_PROGRESS);
			assertThat(agreementsOf(rental)).singleElement()
				.extracting(RentalCancelAgreement::getStatus)
				.isEqualTo(CancelAgreementStatus.REQUESTED);
		}
	}

	@Test
	@DisplayName("같은 요청에 ACCEPT·REJECT가 동시에 오면 하나만 성공하고 나머지는 CLOSED, 대여 상태·이력은 이긴 쪽과 맞는다")
	void concurrentAcceptAndReject() throws InterruptedException {
		for (Rental rental : rentals) {
			// given
			Long agreementId = rentalCancelService.requestAgreement(rental.getId(), renter.getId(), REQUEST)
				.agreementId();

			// when
			Result result = runConcurrently(List.of(
				() -> respond(rental, agreementId, CancelAgreementAction.ACCEPT),
				() -> respond(rental, agreementId, CancelAgreementAction.REJECT)));

			// then
			assertThat(result.unexpected()).isEmpty();
			assertThat(result.successCount()).isEqualTo(1);
			assertThat(result.errors()).containsExactly(ErrorCode.RENTAL_CANCEL_AGREEMENT_CLOSED);
			CancelAgreementStatus agreementStatus = agreementRepository.findById(agreementId).orElseThrow()
				.getStatus();
			RentalStatus rentalStatus = rentalRepository.findById(rental.getId()).orElseThrow().getStatus();
			long canceledHistories = canceledHistoryCountOf(rental);
			if (agreementStatus == CancelAgreementStatus.ACCEPTED) {
				assertThat(rentalStatus).isEqualTo(RentalStatus.CANCELED);
				assertThat(canceledHistories).isEqualTo(1);
			} else {
				assertThat(agreementStatus).isEqualTo(CancelAgreementStatus.REJECTED);
				assertThat(rentalStatus).isEqualTo(RentalStatus.RESERVED);
				assertThat(canceledHistories).isZero();
			}
		}
	}

	private Object respond(Rental rental, Long agreementId, CancelAgreementAction action) {
		return rentalCancelService.respondAgreement(rental.getId(), agreementId, owner.getId(),
			new CancelAgreementRespondRequest(action));
	}

	private List<RentalCancelAgreement> agreementsOf(Rental rental) {
		return agreementRepository.findAll().stream()
			.filter(agreement -> agreement.getRentalId().equals(rental.getId()))
			.toList();
	}

	private long canceledHistoryCountOf(Rental rental) {
		return historyRepository.findAll().stream()
			.filter(history -> history.getRentalId().equals(rental.getId()))
			.filter(history -> history.getToStatus() == RentalStatus.CANCELED)
			.count();
	}

	private Result runConcurrently(List<Callable<?>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(tasks.size());
		AtomicInteger successCount = new AtomicInteger();
		ConcurrentLinkedQueue<ErrorCode> errors = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
		try {
			for (Callable<?> task : tasks) {
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
			assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
		return new Result(successCount.get(), List.copyOf(errors), List.copyOf(unexpected));
	}

	private record Result(int successCount, List<ErrorCode> errors, List<Throwable> unexpected) {
	}
}
