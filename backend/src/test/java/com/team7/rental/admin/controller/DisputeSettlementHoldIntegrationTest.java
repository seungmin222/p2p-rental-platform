package com.team7.rental.admin.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementAmount;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 분쟁 접수에 따른 정산 보류 (REQ-063). 접수는 POST /api/inquiries, 완료·취소도 실제 API로 해서 트랜잭션·락 경계를 운영과 같게 둔다.
 * 대여료 30,000원(3일 × 10,000원) + 보증금 50,000원, 수수료 10% → 지급액 27,000원. 2026-11-06은 금요일이다.
 * 분쟁 접수와 지급 배치가 겹치는 동시성은 성능 실험 때 다룬다(docs/prompts/chore/admin-inquiry-test/notes.md).
 */
class DisputeSettlementHoldIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final LocalDateTime NOW = RETURNED_AT.plusDays(1);
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";

	@Autowired
	private SettlementRepository settlementRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;
	private int nextStart;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
		paymentFixture.createApproved(rental);
	}

	@Test
	@DisplayName("분쟁 접수 → 대여 완료: 정산이 처음부터 HELD로 생긴다")
	void disputeBeforeCompletion() throws Exception {
		dispute(renter, rental).andExpect(status().isCreated());
		assertThat(settlementRepository.findAll()).isEmpty();

		confirmNormal().andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));

		Settlement settlement = settlementOf(rental);
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.HELD);
		assertThat(settlement.getPayoutAmount()).isEqualTo(27_000);
	}

	@Test
	@DisplayName("대여 완료 → 분쟁 접수: 정산이 SCHEDULED에서 HELD로 바뀌고 지급 예정일은 그대로다")
	void disputeAfterCompletion() throws Exception {
		confirmNormal().andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
		Settlement before = settlementOf(rental);
		assertThat(before.getStatus()).isEqualTo(SettlementStatus.SCHEDULED);

		dispute(owner, rental).andExpect(status().isCreated());

		Settlement after = settlementOf(rental);
		assertThat(after.getStatus()).isEqualTo(SettlementStatus.HELD);
		assertThat(after.getScheduledDate()).isEqualTo(before.getScheduledDate());
	}

	@Test
	@DisplayName("이미 지급(PAID)된 정산은 분쟁을 접수해도 PAID 그대로이고, 문의는 접수된다")
	void paidStaysPaid() throws Exception {
		confirmNormal().andExpect(status().isOk());
		Settlement settlement = settlementOf(rental);
		LocalDateTime paidAt = settlement.getScheduledDate().atTime(0, 30);
		settlement.pay(paidAt);
		settlementRepository.save(settlement);
		testClock.fixAt(paidAt.plusHours(1));

		dispute(renter, rental).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("RECEIVED"));

		Settlement after = settlementOf(rental);
		assertThat(after.getStatus()).isEqualTo(SettlementStatus.PAID);
		assertThat(after.getPaidAt()).isEqualTo(paidAt);
	}

	@Test
	@DisplayName("이미 HELD면 상대방의 두 번째 분쟁 접수 뒤에도 HELD 그대로다")
	void heldStaysHeld() throws Exception {
		confirmNormal().andExpect(status().isOk());
		dispute(renter, rental).andExpect(status().isCreated());
		assertThat(settlementOf(rental).getStatus()).isEqualTo(SettlementStatus.HELD);

		dispute(owner, rental).andExpect(status().isCreated());

		assertThat(settlementOf(rental).getStatus()).isEqualTo(SettlementStatus.HELD);
	}

	@Test
	@DisplayName("수수료 있는 취소(CANCEL_FEE)로 생긴 정산도 분쟁을 접수하면 HELD")
	void cancelFeeHeld() throws Exception {
		Rental reserved = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START.plusDays(10),
			END.plusDays(10));
		paymentFixture.createApproved(reserved);
		// 시작일(11/13) 2일 전 → 수수료 30%
		testClock.fixAt(LocalDateTime.of(2026, 11, 11, 9, 0));
		mockMvc.perform(patch("/api/rentals/{id}/cancel", reserved.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"SCHEDULE_CHANGE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));
		Settlement settlement = settlementOf(reserved);
		assertThat(settlement.getSource()).isEqualTo(SettlementSource.CANCEL_FEE);
		assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.SCHEDULED);

		dispute(owner, reserved).andExpect(status().isCreated());

		assertThat(settlementOf(reserved).getStatus()).isEqualTo(SettlementStatus.HELD);
	}

	@Test
	@DisplayName("정산 목록 합계: heldTotal은 HELD 지급액 합이고, scheduledTotal·paidTotal에는 들어가지 않는다")
	void heldTotal() throws Exception {
		Settlement held1 = settle(SettlementAmount.ofCompletion(30_000, 0, 0, 0, 10));
		Settlement held2 = settle(SettlementAmount.ofCompletion(20_000, 0, 0, 5_000, 10));
		settle(SettlementAmount.ofCompletion(10_000, 0, 0, 0, 10));
		Settlement paid = settle(SettlementAmount.ofCompletion(40_000, 0, 0, 0, 10));
		paid.pay(NOW);
		settlementRepository.save(paid);

		dispute(renter, held1.getRentalId()).andExpect(status().isCreated());
		dispute(owner, held2.getRentalId()).andExpect(status().isCreated());

		mockMvc.perform(get("/api/members/me/settlements")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk())
			// held: 27,000 + (18,000 + 5,000)
			.andExpect(jsonPath("$.data.heldTotal").value(50_000))
			.andExpect(jsonPath("$.data.scheduledTotal").value(9_000))
			.andExpect(jsonPath("$.data.paidTotal").value(36_000))
			.andExpect(jsonPath("$.data.settlements.items[?(@.settlementId == " + held1.getId() + ")].status")
				.value("HELD"));
	}

	/** 이 제공자의 COMPLETED 대여와 SCHEDULED 정산(예정일 = 오늘) */
	private Settlement settle(SettlementAmount amount) {
		LocalDate start = LocalDate.of(2026, 10, 1).plusDays(nextStart);
		nextStart += 2;
		Rental completed = rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, start, start.plusDays(1));
		return settlementRepository.save(Settlement.schedule(completed.getId(), owner.getId(),
			SettlementSource.COMPLETED, amount, NOW.toLocalDate()));
	}

	private ResultActions dispute(Member member, Rental target) throws Exception {
		return dispute(member, target.getId());
	}

	private ResultActions dispute(Member member, Long rentalId) throws Exception {
		return mockMvc.perform(post("/api/inquiries").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\": \"DISPUTE\", \"rentalId\": " + rentalId + ", \"content\": \"정산에 이의가 있습니다.\"}"));
	}

	private ResultActions confirmNormal() throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", rental.getId())
			.file(json(NORMAL))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private Settlement settlementOf(Rental target) {
		return settlementRepository.findByRentalId(target.getId()).orElseThrow();
	}
}
