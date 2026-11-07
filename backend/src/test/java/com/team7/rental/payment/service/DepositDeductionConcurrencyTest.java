package com.team7.rental.payment.service;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.batch.DeductionAutoAcceptJob;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DeductionProposal;
import com.team7.rental.payment.entity.DeductionProposalStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.repository.DeductionProposalRepository;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 차감 확정 경합 (REQ-049). 대여 행 락 → 결제 행 락으로 한 줄로 서므로 어느 쪽이 이기든 확정·PG 부분 취소·완료 이력은 한 번이다.
 * 보증금 50,000원, B 정액 10,000원 제안 → 반환 40,000원.
 */
class DepositDeductionConcurrencyTest extends IntegrationTestSupport {

	private static final int ROUNDS = 5;
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime HELD_AT = END.atTime(20, 0);
	private static final LocalDateTime PROPOSED_AT = HELD_AT.plusHours(1);
	private static final LocalDateTime DEADLINE = PROPOSED_AT.plusHours(72);

	@Autowired
	private DeductionAutoAcceptJob autoAcceptJob;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private DeductionProposalRepository proposalRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("기한 직전 대여자 수락과 기한이 지난 회차의 자동 수락이 겹쳐도 확정은 한 번이고, 이긴 쪽의 상태·이력이 남는다")
	void acceptVersusAutoAccept() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			// given: 대여자 요청은 기한 1초 전 시계, 배치는 기한 시각으로 돈다
			Rental rental = heldRentalWithProposal(PROPOSED_AT);
			int cancelsBefore = paymentGateway.cancelCalls();
			testClock.fixAt(DEADLINE.minusSeconds(1));
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);

			// when
			Future<MvcResult> accept;
			Future<Integer> batch;
			try {
				accept = executor.submit(() -> {
					start.await();
					return respond(rental, "ACCEPT");
				});
				batch = executor.submit(() -> {
					start.await();
					return autoAcceptJob.acceptDue(DEADLINE);
				});
				start.countDown();
				int acceptStatus = accept.get(30, TimeUnit.SECONDS).getResponse().getStatus();
				int batchAccepted = batch.get(30, TimeUnit.SECONDS);

				// then
				DeductionProposal proposal = proposalOf(rental);
				if (proposal.getStatus() == DeductionProposalStatus.ACCEPTED) {
					assertThat(acceptStatus).isEqualTo(200);
					assertThat(batchAccepted).isZero();
				} else {
					assertThat(proposal.getStatus()).isEqualTo(DeductionProposalStatus.AUTO_ACCEPTED);
					assertThat(acceptStatus).isEqualTo(409);
					assertThat(batchAccepted).isEqualTo(1);
				}
				assertSettledOnce(rental, cancelsBefore);
			} finally {
				executor.shutdownNow();
			}
		}
	}

	@Test
	@DisplayName("대여자 수락과 관리자 확정이 겹치면 수락만 200이고 관리자는 409, 확정은 한 번이다")
	void acceptVersusAdmin() throws Exception {
		Member admin = fixture.createMember();
		for (int round = 0; round < ROUNDS; round++) {
			// given: 보류 7일이 지났고 기한 전 제안이 열려 있다(관리자 확정 조건은 시각상 만족)
			// 기한(72시간)이 지나지 않게 7일째 1시간 전에 제안했다
			Rental rental = heldRentalWithProposal(HELD_AT.plusDays(7).minusHours(1));
			int cancelsBefore = paymentGateway.cancelCalls();
			testClock.fixAt(HELD_AT.plusDays(7));
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);

			// when
			try {
				Future<MvcResult> accept = executor.submit(() -> {
					start.await();
					return respond(rental, "ACCEPT");
				});
				Future<MvcResult> decide = executor.submit(() -> {
					start.await();
					return mockMvc.perform(patch("/api/admin/rentals/{id}/deposit", rental.getId())
						.header(HttpHeaders.AUTHORIZATION, adminBearerToken(admin.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"grade\": \"B\", \"amount\": 5000, \"reason\": \"관리자\"}")).andReturn();
				});
				start.countDown();

				// then
				assertThat(accept.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
				assertThat(decide.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(409);
				assertThat(proposalOf(rental).getStatus()).isEqualTo(DeductionProposalStatus.ACCEPTED);
				assertSettledOnce(rental, cancelsBefore);
			} finally {
				executor.shutdownNow();
			}
		}
	}

	/** 제안 시각은 감사 필드(created_at)라 저장 때의 시계로 정한다 */
	private Rental heldRentalWithProposal(LocalDateTime proposedAt) {
		testClock.fixAt(proposedAt);
		Rental rental = rentalFixture.createReturned(item, renter, START, END, HELD_AT.minusHours(2));
		Payment payment = paymentFixture.createApproved(rental);
		payment.holdDeposit(HELD_AT);
		paymentRepository.save(payment);
		proposalRepository.save(DeductionProposal.propose(rental.getId(), owner.getId(), DamageGrade.B, 10_000, "오염",
			List.of("/files/deductions/a.jpg"), List.of()));
		return rental;
	}

	private MvcResult respond(Rental rental, String action) throws Exception {
		Long proposalId = proposalOf(rental).getId();
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{id}/deposit", rental.getId())
			.file(json("{\"action\": \"" + action + "\", \"proposalId\": " + proposalId + "}"))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))).andReturn();
	}

	private DeductionProposal proposalOf(Rental rental) {
		return proposalRepository.findFirstByRentalIdOrderByIdDesc(rental.getId()).orElseThrow();
	}

	private void assertSettledOnce(Rental rental, int cancelsBefore) {
		assertThat(paymentGateway.cancelCalls() - cancelsBefore).isEqualTo(1);
		Payment payment = paymentRepository.findAll().stream()
			.filter(candidate -> candidate.getRentalId().equals(rental.getId()))
			.findFirst().orElseThrow();
		assertThat(payment.getDeductionAmount()).isEqualTo(10_000);
		assertThat(payment.getRefundedAmount()).isEqualTo(40_000);
		assertThat(historyRepository.findAll().stream()
			.filter(history -> history.getRentalId().equals(rental.getId()))
			.map(RentalStatusHistory::getReason)
			.toList()).singleElement().isNotNull();
		assertThat(proposalRepository.findAll().stream()
			.filter(proposal -> proposal.getRentalId().equals(rental.getId()))
			.toList()).hasSize(1);
	}
}
