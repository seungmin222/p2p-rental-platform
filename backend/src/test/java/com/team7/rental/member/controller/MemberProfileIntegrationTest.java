package com.team7.rental.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 사용자 프로필·받은 후기·등록 물품 (REQ-021) */
class MemberProfileIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
	private static final LocalDate PERIOD_START = LocalDate.of(2026, 10, 1);
	private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember owner;
	private VerifiedMember renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember();
		item = fixture.createItem(owner, PERIOD_START, PERIOD_END).item();
	}

	@Test
	@DisplayName("프로필: 비회원도 200. 닉네임·사진·대표 동네·가입일을 주고 이메일은 주지 않는다")
	void profile() throws Exception {
		Member member = owner.member();

		mockMvc.perform(get("/api/members/{id}", member.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberId").value(member.getId()))
			.andExpect(jsonPath("$.data.nickname").value(member.getNickname()))
			.andExpect(jsonPath("$.data.regionName").value(owner.region().getName()))
			.andExpect(jsonPath("$.data.joinedAt").exists())
			.andExpect(jsonPath("$.data.completedRentalCount").value(0))
			.andExpect(jsonPath("$.data.isWithdrawn").value(false))
			.andExpect(jsonPath("$.data.email").doesNotExist())
			.andExpect(jsonPath("$.data.role").doesNotExist());
	}

	@Test
	@DisplayName("프로필: 로그인해서 봐도 같고, 대표 동네가 없으면 regionName null")
	void profileWithoutRegion() throws Exception {
		Member noRegion = fixture.createMember();

		mockMvc.perform(get("/api/members/{id}", noRegion.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.member().getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value(noRegion.getNickname()))
			.andExpect(jsonPath("$.data.regionName").doesNotExist());
	}

	@Test
	@DisplayName("거래 횟수: 대여자·제공자 양쪽의 COMPLETED만 센다")
	void completedRentalCount() throws Exception {
		VerifiedMember other = fixture.createVerifiedMember();
		Item otherItem = fixture.createItem(other, PERIOD_START, PERIOD_END).item();
		// owner는 제공자로 2건, 대여자로 1건 완료
		rentalFixture.createRental(item, renter.member(), RentalStatus.COMPLETED, date(1), date(2));
		rentalFixture.createRental(item, renter.member(), RentalStatus.COMPLETED, date(3), date(4));
		rentalFixture.createRental(otherItem, owner.member(), RentalStatus.COMPLETED, date(1), date(2));
		// 완료가 아닌 대여는 세지 않는다
		rentalFixture.createRental(item, renter.member(), RentalStatus.RETURNED, date(5), date(6));
		rentalFixture.createRental(item, renter.member(), RentalStatus.CANCELED, date(7), date(8));
		rentalFixture.createRental(item, other.member(), RentalStatus.RENTING, date(9), date(10));

		mockMvc.perform(get("/api/members/{id}", owner.member().getId()))
			.andExpect(jsonPath("$.data.completedRentalCount").value(3));
		mockMvc.perform(get("/api/members/{id}", renter.member().getId()))
			.andExpect(jsonPath("$.data.completedRentalCount").value(2));
		mockMvc.perform(get("/api/members/{id}", other.member().getId()))
			.andExpect(jsonPath("$.data.completedRentalCount").value(1));
	}

	@Test
	@DisplayName("없는 회원이면 프로필·받은 후기·등록 물품 모두 404 MEMBER_NOT_FOUND, 숫자가 아닌 ID는 400")
	void notFound() throws Exception {
		for (String path : new String[] {"/api/members/{id}", "/api/members/{id}/reviews",
			"/api/members/{id}/items"}) {
			mockMvc.perform(get(path, 999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
		}
		mockMvc.perform(get("/api/members/{id}", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("탈퇴 회원: 프로필은 200 isWithdrawn true·나머지 null, 받은 후기·등록 물품은 빈 목록")
	void withdrawn() throws Exception {
		rentalFixture.createRental(item, renter.member(), RentalStatus.COMPLETED, date(1), date(2));
		jdbcTemplate.update("""
			INSERT INTO reviews (rental_id, writer_id, target_id, satisfaction, created_at, updated_at)
			SELECT id, renter_id, owner_id, 'GOOD', NOW(6), NOW(6) FROM rentals""");
		jdbcTemplate.update("UPDATE members SET status = 'WITHDRAWN' WHERE id = ?", owner.member().getId());

		mockMvc.perform(get("/api/members/{id}", owner.member().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberId").value(owner.member().getId()))
			.andExpect(jsonPath("$.data.isWithdrawn").value(true))
			.andExpect(jsonPath("$.data.nickname").doesNotExist())
			.andExpect(jsonPath("$.data.profileImageUrl").doesNotExist())
			.andExpect(jsonPath("$.data.regionName").doesNotExist())
			.andExpect(jsonPath("$.data.joinedAt").doesNotExist())
			.andExpect(jsonPath("$.data.completedRentalCount").doesNotExist());
		mockMvc.perform(get("/api/members/{id}/reviews", owner.member().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get("/api/members/{id}/items", owner.member().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("등록 물품: 그 회원의 AVAILABLE만 bumpedAt·id 내림차순. HIDDEN·DELETED·남의 물품은 빠진다")
	void items() throws Exception {
		Item newer = fixture.createItem(owner, PERIOD_START, PERIOD_END).item();
		Item sameTime = fixture.createItem(owner, PERIOD_START, PERIOD_END).item();
		Item hidden = fixture.createItem(owner, PERIOD_START, PERIOD_END).item();
		Item deleted = fixture.createItem(owner, PERIOD_START, PERIOD_END).item();
		fixture.createItem(renter, PERIOD_START, PERIOD_END);
		jdbcTemplate.update("UPDATE items SET bumped_at = ? WHERE id = ?", NOW.minusDays(2), item.getId());
		jdbcTemplate.update("UPDATE items SET bumped_at = ? WHERE id IN (?, ?)", NOW.minusDays(1), newer.getId(),
			sameTime.getId());
		jdbcTemplate.update("UPDATE items SET status = 'HIDDEN' WHERE id = ?", hidden.getId());
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", deleted.getId());

		mockMvc.perform(get("/api/members/{id}/items", owner.member().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(3))
			.andExpect(jsonPath("$.data.items[0].itemId").value(sameTime.getId()))
			.andExpect(jsonPath("$.data.items[1].itemId").value(newer.getId()))
			.andExpect(jsonPath("$.data.items[2].itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.items[0].regionName").value(owner.region().getName()))
			.andExpect(jsonPath("$.data.items[0].isRenting").value(false))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("등록 물품: 다음 페이지는 빈 목록, 대여 중이면 isRenting true, 음수 page는 400")
	void itemsPagingAndRenting() throws Exception {
		rentalFixture.createRental(item, renter.member(), RentalStatus.RENTING, NOW.toLocalDate().minusDays(1),
			NOW.toLocalDate().plusDays(1));

		mockMvc.perform(get("/api/members/{id}/items", owner.member().getId()))
			.andExpect(jsonPath("$.data.items[0].isRenting").value(true));
		mockMvc.perform(get("/api/members/{id}/items", owner.member().getId()).queryParam("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(0))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get("/api/members/{id}/items", owner.member().getId()).queryParam("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	private static LocalDate date(int day) {
		return LocalDate.of(2026, 10, day);
	}
}
