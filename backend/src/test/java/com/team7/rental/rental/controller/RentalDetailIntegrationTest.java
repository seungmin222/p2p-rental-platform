package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

import jakarta.persistence.EntityManagerFactory;

/** GET /api/rentals/{id} (REQ-016) */
class RentalDetailIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
	}

	@Test
	@DisplayName("대여자가 조회하면 myRole=RENTER, 상대는 제공자다")
	void renterView() throws Exception {
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.rentalId").value(rental.getId()))
			.andExpect(jsonPath("$.data.status").value("RESERVED"))
			.andExpect(jsonPath("$.data.myRole").value("RENTER"))
			.andExpect(jsonPath("$.data.counterpart.memberId").value(owner.getId()))
			.andExpect(jsonPath("$.data.counterpart.nickname").value(owner.getNickname()))
			.andExpect(jsonPath("$.data.item.name").value(item.getName()))
			.andExpect(jsonPath("$.data.item.isDeleted").value(false))
			.andExpect(jsonPath("$.data.cancelFee").value(0))
			.andExpect(jsonPath("$.data.activeCancelAgreement").doesNotExist());
	}

	@Test
	@DisplayName("제공자가 조회하면 myRole=OWNER, 상대는 대여자다")
	void ownerView() throws Exception {
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(owner.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myRole").value("OWNER"))
			.andExpect(jsonPath("$.data.counterpart.memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data.counterpart.nickname").value(renter.getNickname()));
	}

	@Test
	@DisplayName("물품이 삭제돼도 상세는 조회되고 item.isDeleted=true다")
	void deletedItem() throws Exception {
		// given
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", item.getId());

		// when, then
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.item.isDeleted").value(true));
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void notParticipant() throws Exception {
		// given
		Member stranger = fixture.createMember();

		// when, then
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(stranger.getId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("없는 대여는 404 RENTAL_NOT_FOUND다")
	void notFound() throws Exception {
		mockMvc.perform(get("/api/rentals/{id}", rental.getId() + 999).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
	}

	@Test
	@DisplayName("토큰이 없으면 401 AUTH_UNAUTHORIZED다")
	void unauthorized() throws Exception {
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("상세 조회는 쿼리 5개로 끝난다(대여·물품 요약·대표 이미지·상대 닉네임·진행 중인 동의 취소)")
	void queryCount() throws Exception {
		// given: 픽스처 저장이 섞이지 않게 요청 직전에 비운다
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		// when
		mockMvc.perform(get("/api/rentals/{id}", rental.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(status().isOk());

		// then
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(5);
	}
}
