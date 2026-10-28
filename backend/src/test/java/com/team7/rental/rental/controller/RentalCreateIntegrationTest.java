package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemAvailablePeriod;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** POST /api/rentals (REQ-013·014). 오늘은 10/28, 물품 가능 기간은 [10/28, 12/31] */
class RentalCreateIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate TODAY = NOW.toLocalDate();
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalStatusHistoryRepository rentalStatusHistoryRepository;

	@Autowired
	private ItemAvailablePeriodRepository itemAvailablePeriodRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private VerifiedMember owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, TODAY, LocalDate.of(2026, 12, 31)).item();
	}

	@Test
	@DisplayName("예약하면 201과 대여 상세를 돌려주고, 1순위 단계라 RESERVED로 저장하며 생성 이력(from=null)을 남긴다")
	void create() throws Exception {
		// when
		ResultActions result = reserve(renter.getId(), item.getId(), START, END);

		// then
		result.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("RESERVED"))
			.andExpect(jsonPath("$.data.myRole").value("RENTER"))
			.andExpect(jsonPath("$.data.item.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.counterpart.memberId").value(owner.member().getId()))
			.andExpect(jsonPath("$.data.counterpart.nickname").value(owner.member().getNickname()))
			.andExpect(jsonPath("$.data.startDate").value("2026-11-03"))
			.andExpect(jsonPath("$.data.endDate").value("2026-11-05"))
			.andExpect(jsonPath("$.data.rentalDays").value(3))
			.andExpect(jsonPath("$.data.rentalFee").value(MemberItemFixture.PRICE_PER_DAY * 3))
			.andExpect(jsonPath("$.data.deposit").value(MemberItemFixture.DEPOSIT))
			.andExpect(
				jsonPath("$.data.totalAmount").value(MemberItemFixture.PRICE_PER_DAY * 3 + MemberItemFixture.DEPOSIT))
			.andExpect(jsonPath("$.data.paymentExpiresAt").doesNotExist());

		Rental rental = rentalRepository.findAll().getFirst();
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(rental.getOwnerId()).isEqualTo(owner.member().getId());
		List<RentalStatusHistory> histories = rentalStatusHistoryRepository.findAll();
		assertThat(histories).singleElement().satisfies(history -> {
			assertThat(history.getRentalId()).isEqualTo(rental.getId());
			assertThat(history.getFromStatus()).isNull();
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RESERVED);
			assertThat(history.getChangedBy()).isEqualTo(renter.getId());
		});
	}

	@Test
	@DisplayName("오늘 시작하는 1일 대여와 최대 30일 대여는 가능하다")
	void periodBoundaryAllowed() throws Exception {
		reserve(renter.getId(), item.getId(), TODAY, TODAY).andExpect(status().isCreated());
		reserve(renter.getId(), item.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("지난 시작일, 시작일 > 종료일, 31일 대여는 400 RENTAL_INVALID_PERIOD다")
	void invalidPeriod() throws Exception {
		expectError(reserve(renter.getId(), item.getId(), TODAY.minusDays(1), START), 400, "RENTAL_INVALID_PERIOD");
		expectError(reserve(renter.getId(), item.getId(), END, START), 400, "RENTAL_INVALID_PERIOD");
		expectError(reserve(renter.getId(), item.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)), 400,
			"RENTAL_INVALID_PERIOD");
		assertThat(rentalRepository.count()).isZero();
	}

	@Test
	@DisplayName("필수 값이 없으면 400 COMMON_INVALID_INPUT이고 필드 오류를 알려 준다")
	void missingField() throws Exception {
		// when
		ResultActions result = mockMvc.perform(post("/api/rentals")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"itemId\": " + item.getId() + ", \"startDate\": \"2026-11-03\"}"));

		// then
		expectError(result, 400, "COMMON_INVALID_INPUT")
			.andExpect(jsonPath("$.error.details[0].field").value("endDate"));
	}

	@Test
	@DisplayName("가능 기간 두 개에 걸친 기간은 각각에 포함되지 않으므로 400 RENTAL_OUT_OF_AVAILABLE_PERIOD다")
	void acrossTwoAvailablePeriods() throws Exception {
		// given: [11/1, 11/10] + [11/11, 11/20]
		Item splitItem = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)).item();
		itemAvailablePeriodRepository.save(
			ItemAvailablePeriod.create(splitItem.getId(), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 20)));

		// when, then
		expectError(reserve(renter.getId(), splitItem.getId(), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 12)),
			400, "RENTAL_OUT_OF_AVAILABLE_PERIOD");
		reserve(renter.getId(), splitItem.getId(), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 20))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("가능 기간 밖은 400 RENTAL_OUT_OF_AVAILABLE_PERIOD다")
	void outOfAvailablePeriod() throws Exception {
		expectError(reserve(renter.getId(), item.getId(), LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)), 400,
			"RENTAL_OUT_OF_AVAILABLE_PERIOD");
	}

	@Test
	@DisplayName("본인 물품은 400 RENTAL_OWN_ITEM이고, 본인의 숨김 물품도 같다")
	void ownItem() throws Exception {
		expectError(reserve(owner.member().getId(), item.getId(), START, END), 400, "RENTAL_OWN_ITEM");

		updateItemStatus("HIDDEN");
		expectError(reserve(owner.member().getId(), item.getId(), START, END), 400, "RENTAL_OWN_ITEM");
	}

	@Test
	@DisplayName("없는 물품, 삭제된 물품, 남의 숨김 물품은 404 ITEM_NOT_FOUND다")
	void itemNotFound() throws Exception {
		expectError(reserve(renter.getId(), item.getId() + 999, START, END), 404, "ITEM_NOT_FOUND");

		updateItemStatus("HIDDEN");
		expectError(reserve(renter.getId(), item.getId(), START, END), 404, "ITEM_NOT_FOUND");

		updateItemStatus("DELETED");
		expectError(reserve(renter.getId(), item.getId(), START, END), 404, "ITEM_NOT_FOUND");
	}

	@Test
	@DisplayName("대표 동네가 없거나 인증 전이면 403 REGION_NOT_VERIFIED다")
	void regionNotVerified() throws Exception {
		// given
		Member noRegion = fixture.createMember();
		Member unverified = fixture.createMember();
		fixture.addPrimaryRegion(unverified, fixture.createRegion(), null);

		// when, then
		expectError(reserve(noRegion.getId(), item.getId(), START, END), 403, "REGION_NOT_VERIFIED");
		expectError(reserve(unverified.getId(), item.getId(), START, END), 403, "REGION_NOT_VERIFIED");
	}

	@Test
	@DisplayName("동네 인증 후 정확히 30일(region.verify-valid-days)이 지나면 만료돼 403, 1초 전이면 예약된다")
	void regionVerificationExpired() throws Exception {
		// given
		Member expired = fixture.createMember();
		fixture.addPrimaryRegion(expired, fixture.createRegion(), NOW.minusDays(30));
		Member almost = fixture.createMember();
		fixture.addPrimaryRegion(almost, fixture.createRegion(), NOW.minusDays(30).plusSeconds(1));

		// when, then
		expectError(reserve(expired.getId(), item.getId(), START, END), 403, "REGION_NOT_VERIFIED");
		reserve(almost.getId(), item.getId(), START, END).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("토큰이 없으면 401 AUTH_UNAUTHORIZED다")
	void unauthorized() throws Exception {
		// when
		ResultActions result = mockMvc.perform(post("/api/rentals")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(item.getId(), START, END)));

		// then
		expectError(result, 401, "AUTH_UNAUTHORIZED");
	}

	@Test
	@DisplayName("이미 예약된 기간과 하루라도 겹치면 409 RENTAL_PERIOD_CONFLICT다")
	void conflict() throws Exception {
		// given
		reserve(renter.getId(), item.getId(), START, END).andExpect(status().isCreated());
		Member another = fixture.createVerifiedMember().member();

		// when, then: 기존 종료일 == 새 시작일
		expectError(reserve(another.getId(), item.getId(), END, END.plusDays(2)), 409, "RENTAL_PERIOD_CONFLICT");
		reserve(another.getId(), item.getId(), END.plusDays(1), END.plusDays(2)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("만료된 결제 대기의 기간은 다시 예약할 수 있다")
	void expiredPendingPaymentReleasesPeriod() throws Exception {
		// given: 만료 시각이 정확히 지금인 결제 대기
		rentalFixture.createPendingPayment(item, renter, START, END, NOW);
		Member another = fixture.createVerifiedMember().member();

		// when, then
		reserve(another.getId(), item.getId(), START, END).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("만료 전인 결제 대기의 기간은 409 RENTAL_PERIOD_CONFLICT다")
	void activePendingPaymentBlocksPeriod() throws Exception {
		// given
		rentalFixture.createPendingPayment(item, renter, START, END, NOW.plusSeconds(1));
		Member another = fixture.createVerifiedMember().member();

		// when, then
		expectError(reserve(another.getId(), item.getId(), START, END), 409, "RENTAL_PERIOD_CONFLICT");
	}

	@Test
	@DisplayName("예약 후 물품 가격·보증금을 바꿔도 대여 금액은 예약 시점 값 그대로다")
	void priceSnapshot() throws Exception {
		// given
		reserve(renter.getId(), item.getId(), START, END).andExpect(status().isCreated());
		Long rentalId = rentalRepository.findAll().getFirst().getId();

		// when
		jdbcTemplate.update("UPDATE items SET price_per_day = 99000, deposit = 999000 WHERE id = ?", item.getId());

		// then
		mockMvc
			.perform(get("/api/rentals/{id}", rentalId).header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pricePerDay").value(MemberItemFixture.PRICE_PER_DAY))
			.andExpect(jsonPath("$.data.rentalFee").value(MemberItemFixture.PRICE_PER_DAY * 3))
			.andExpect(jsonPath("$.data.deposit").value(MemberItemFixture.DEPOSIT));
	}

	private ResultActions reserve(Long memberId, Long itemId, LocalDate start, LocalDate end) throws Exception {
		return mockMvc.perform(post("/api/rentals")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(itemId, start, end)));
	}

	private String body(Long itemId, LocalDate start, LocalDate end) {
		return "{\"itemId\": %d, \"startDate\": \"%s\", \"endDate\": \"%s\"}".formatted(itemId, start, end);
	}

	private ResultActions expectError(ResultActions result, int status, String code) throws Exception {
		return result.andExpect(status().is(status))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value(code));
	}

	// 숨기기(REQ-044)·삭제(REQ-008) API가 아직 없어 상태를 직접 바꾼다
	private void updateItemStatus(String status) {
		jdbcTemplate.update("UPDATE items SET status = ? WHERE id = ?", status, item.getId());
	}
}
