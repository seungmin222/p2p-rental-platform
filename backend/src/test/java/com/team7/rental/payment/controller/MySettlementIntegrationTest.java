package com.team7.rental.payment.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementAmount;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 내 정산 내역 (REQ-039, GET /api/members/me/settlements) */
class MySettlementIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 9, 0);

	@Autowired
	private SettlementRepository settlementRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private int nextStart;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).item();
	}

	@Test
	@DisplayName("본인 정산을 최신 순으로 금액·수수료 내역과 대여 정보와 함께 돌려주고, 합계는 상태별 전체 기준이다")
	void listWithTotals() throws Exception {
		Settlement paid = settle(SettlementAmount.ofCompletion(30_000, 0, 15_000, 20_000, 10),
			SettlementSource.COMPLETED);
		paid.pay(paid.getScheduledDate().atTime(0, 30));
		settlementRepository.save(paid);
		Settlement scheduled = settle(SettlementAmount.ofCancelFee(9_000, 10), SettlementSource.CANCEL_FEE);
		// 다른 제공자의 정산은 보이지 않는다
		VerifiedMember other = fixture.createVerifiedMember();
		Item otherItem = fixture.createItem(other, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).item();
		Rental otherRental = rentalFixture.createRental(otherItem, renter, RentalStatus.COMPLETED,
			LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
		settlementRepository.save(Settlement.schedule(otherRental.getId(), other.member().getId(),
			SettlementSource.COMPLETED, SettlementAmount.ofCompletion(50_000, 0, 0, 0, 10), NOW.toLocalDate()));

		get(owner, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.scheduledTotal").value(8_100))
			.andExpect(jsonPath("$.data.paidTotal").value(60_500))
			.andExpect(jsonPath("$.data.settlements.totalElements").value(2))
			.andExpect(jsonPath("$.data.settlements.items[0].settlementId").value(scheduled.getId()))
			.andExpect(jsonPath("$.data.settlements.items[0].source").value("CANCEL_FEE"))
			.andExpect(jsonPath("$.data.settlements.items[0].status").value("SCHEDULED"))
			.andExpect(jsonPath("$.data.settlements.items[0].paidAt").doesNotExist())
			.andExpect(jsonPath("$.data.settlements.items[1].rentalId").value(paid.getRentalId()))
			.andExpect(jsonPath("$.data.settlements.items[1].item.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.settlements.items[1].item.name").value(item.getName()))
			.andExpect(jsonPath("$.data.settlements.items[1].startDate").exists())
			.andExpect(jsonPath("$.data.settlements.items[1].grossAmount").value(45_000))
			.andExpect(jsonPath("$.data.settlements.items[1].feeAmount").value(4_500))
			.andExpect(jsonPath("$.data.settlements.items[1].deductionAmount").value(20_000))
			.andExpect(jsonPath("$.data.settlements.items[1].payoutAmount").value(60_500))
			.andExpect(jsonPath("$.data.settlements.items[1].status").value("PAID"))
			.andExpect(jsonPath("$.data.settlements.items[1].paidAt").exists());

		get(renter, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.scheduledTotal").value(0))
			.andExpect(jsonPath("$.data.paidTotal").value(0))
			.andExpect(jsonPath("$.data.settlements.items").isEmpty());
	}

	@Test
	@DisplayName("페이지 크기는 settlement.page(20) 고정이고 page로 다음 페이지를 본다")
	void paging() throws Exception {
		for (int i = 0; i < 21; i++) {
			settle(SettlementAmount.ofCompletion(10_000, 0, 0, 0, 10), SettlementSource.COMPLETED);
		}

		get(owner, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.settlements.size").value(20))
			.andExpect(jsonPath("$.data.settlements.items.length()").value(20))
			.andExpect(jsonPath("$.data.settlements.hasNext").value(true))
			.andExpect(jsonPath("$.data.scheduledTotal").value(21 * 9_000));
		get(owner, "1").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.settlements.items.length()").value(1))
			.andExpect(jsonPath("$.data.settlements.hasNext").value(false));
	}

	@Test
	@DisplayName("page가 음수면 400, 로그인하지 않으면 401")
	void invalid() throws Exception {
		get(owner, "-1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/api/members/me/settlements")).andExpect(status().isUnauthorized());
	}

	private Settlement settle(SettlementAmount amount, SettlementSource source) {
		LocalDate start = LocalDate.of(2026, 1, 1).plusDays(nextStart);
		nextStart += 2;
		RentalStatus status = source == SettlementSource.COMPLETED ? RentalStatus.COMPLETED : RentalStatus.CANCELED;
		Rental rental = rentalFixture.createRental(item, renter, status, start, start.plusDays(1));
		return settlementRepository.save(Settlement.schedule(rental.getId(), owner.getId(), source, amount,
			NOW.toLocalDate()));
	}

	private ResultActions get(Member member, String page) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/api/members/me/settlements").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()));
		if (page != null) {
			request.param("page", page);
		}
		return mockMvc.perform(request);
	}
}
