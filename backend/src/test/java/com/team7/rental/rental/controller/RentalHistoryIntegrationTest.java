package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

import jakarta.persistence.EntityManagerFactory;

/** GET /api/members/me/rentals?role=renter (REQ-019) */
class RentalHistoryIntegrationTest extends IntegrationTestSupport {

	private static final String URL = "/api/members/me/rentals";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private ItemImageRepository itemImageRepository;

	private VerifiedMember owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = createItem(owner);
	}

	@Test
	@DisplayName("7개 상태가 탭별로 나뉘고, tab을 생략하면 ONGOING이다")
	void tabs() throws Exception {
		// given
		Map<RentalStatus, Long> ids = new EnumMap<>(RentalStatus.class);
		ids.put(RentalStatus.PENDING_PAYMENT,
			rentalFixture.createPendingPayment(item, renter, START, END, NOW.plusMinutes(15)).getId());
		for (RentalStatus status : RentalStatus.values()) {
			if (status != RentalStatus.PENDING_PAYMENT) {
				ids.put(status, rentalFixture.createRental(item, renter, status, START, END).getId());
			}
		}

		// when, then
		assertThat(rentalIds(request("UPCOMING"))).containsExactlyInAnyOrder(ids.get(RentalStatus.PENDING_PAYMENT),
			ids.get(RentalStatus.RESERVED));
		assertThat(rentalIds(request("ONGOING"))).containsExactlyInAnyOrder(ids.get(RentalStatus.RENTING),
			ids.get(RentalStatus.OVERDUE), ids.get(RentalStatus.RETURNED));
		assertThat(rentalIds(request("DONE"))).containsExactlyInAnyOrder(ids.get(RentalStatus.COMPLETED),
			ids.get(RentalStatus.CANCELED));
		assertThat(rentalIds(request(null))).containsExactlyInAnyOrderElementsOf(rentalIds(request("ONGOING")));
	}

	@Test
	@DisplayName("결제 시간이 지난 PENDING_PAYMENT도 만료 배치 전까지 UPCOMING에 그대로 보인다")
	void expiredPendingPaymentStillUpcoming() throws Exception {
		// given
		Rental expired = rentalFixture.createPendingPayment(item, renter, START, END, NOW.minusMinutes(1));

		// when, then
		perform(request("UPCOMING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].rentalId").value(expired.getId()))
			.andExpect(jsonPath("$.data.items[0].status").value("PENDING_PAYMENT"));
	}

	@Test
	@DisplayName("남이 빌린 대여와 내가 제공자인 대여는 나오지 않는다")
	void onlyMyRentalsAsRenter() throws Exception {
		// given
		Rental mine = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		Member stranger = fixture.createMember();
		rentalFixture.createRental(item, stranger, RentalStatus.RENTING, START, END);
		// renter가 제공자인 물품을 다른 회원이 빌림
		Item renterItem = createItem(new VerifiedMember(renter, owner.region(), owner.memberRegion()));
		rentalFixture.createRental(renterItem, stranger, RentalStatus.RENTING, START, END);

		// when, then
		assertThat(rentalIds(request("ONGOING"))).containsExactly(mine.getId());
	}

	@Test
	@DisplayName("OVERDUE는 startDate가 더 이른데도 맨 위이고, 그다음 startDate DESC, 같으면 id DESC다")
	void sort() throws Exception {
		// given
		Rental overdue = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START.minusDays(20),
			START.minusDays(18));
		Rental early = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START.minusDays(5), END);
		Rental sameStartFirst = rentalFixture.createRental(item, renter, RentalStatus.RETURNED, START, END);
		Rental sameStartSecond = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);

		// when, then
		assertThat(rentalIds(request("ONGOING"))).containsExactly(overdue.getId(), sameStartSecond.getId(),
			sameStartFirst.getId(), early.getId());
	}

	@Test
	@DisplayName("21건이면 page 0은 20건·hasNext true·totalElements 21, page 1은 1건·hasNext false다")
	void paging() throws Exception {
		// given
		createReserved(21);

		// when, then
		perform(request("UPCOMING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(20))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.totalElements").value(21))
			.andExpect(jsonPath("$.data.hasNext").value(true));
		perform(request("UPCOMING").queryParam("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(21))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("canWriteReview: 완료 + 7일 정각 true, + 1초 false, CANCELED·RENTING false")
	void canWriteReview() throws Exception {
		// given
		Rental atDeadline = createCompleted(NOW.minusDays(7), START);
		Rental pastDeadline = createCompleted(NOW.minusDays(7).minusSeconds(1), START.minusDays(1));
		Rental canceled = rentalFixture.createRental(item, renter, RentalStatus.CANCELED, START.minusDays(2),
			END);
		Rental renting = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);

		// when
		String done = perform(request("DONE")).andReturn().getResponse().getContentAsString();
		String ongoing = perform(request("ONGOING")).andReturn().getResponse().getContentAsString();

		// then
		assertThat(canWriteReviewOf(done, atDeadline)).isTrue();
		assertThat(canWriteReviewOf(done, pastDeadline)).isFalse();
		assertThat(canWriteReviewOf(done, canceled)).isFalse();
		assertThat(canWriteReviewOf(ongoing, renting)).isFalse();
	}

	@Test
	@DisplayName("항목: 삭제 물품은 이름 유지·isDeleted true, 이미지 없으면 thumbnailUrl null, 제공자 닉네임, totalAmount = 대여료 + 보증금")
	void itemFields() throws Exception {
		// given
		Item deleted = createItem(owner);
		itemImageRepository.save(ItemImage.create(item.getId(), "/files/a.jpg", "/files/a_t.jpg", 0));
		Rental withImage = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
		Rental ofDeleted = rentalFixture.createRental(deleted, renter, RentalStatus.RENTING, START.minusDays(1),
			END);
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", deleted.getId());
		long expectedTotal = (long)MemberItemFixture.PRICE_PER_DAY * 3 + MemberItemFixture.DEPOSIT;

		// when, then: startDate DESC라 withImage가 먼저
		perform(request("ONGOING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].rentalId").value(withImage.getId()))
			.andExpect(jsonPath("$.data.items[0].item.itemId").value(item.getId()))
			.andExpect(jsonPath("$.data.items[0].item.thumbnailUrl").value("/files/a_t.jpg"))
			.andExpect(jsonPath("$.data.items[0].item.isDeleted").value(false))
			.andExpect(jsonPath("$.data.items[0].owner.memberId").value(owner.member().getId()))
			.andExpect(jsonPath("$.data.items[0].owner.nickname").value(owner.member().getNickname()))
			.andExpect(jsonPath("$.data.items[0].startDate").value(START.toString()))
			.andExpect(jsonPath("$.data.items[0].endDate").value(END.toString()))
			.andExpect(jsonPath("$.data.items[0].totalAmount").value(expectedTotal))
			.andExpect(jsonPath("$.data.items[1].rentalId").value(ofDeleted.getId()))
			.andExpect(jsonPath("$.data.items[1].item.name").value(deleted.getName()))
			.andExpect(jsonPath("$.data.items[1].item.isDeleted").value(true))
			.andExpect(jsonPath("$.data.items[1].item.thumbnailUrl").value(nullValue()));
	}

	@ParameterizedTest(name = "{0} → field {1}")
	@CsvSource({
		"'tab=ONGOING', role",
		"'role=owner', role",
		"'role=renter&tab=ALL', tab",
		"'role=renter&tab=ongoing', tab",
		"'role=renter&page=-1', page"
	})
	@DisplayName("잘못된 파라미터는 400 COMMON_INVALID_INPUT이고 details에 필드 이름이 있다")
	void invalidInput(String query, String field) throws Exception {
		mockMvc.perform(get(URL + "?" + query).header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value(field))
			.andExpect(jsonPath("$.error.details.length()").value(1));
	}

	@ParameterizedTest(name = "tab={0}")
	@ValueSource(strings = {"ALL", "ongoing"})
	@DisplayName("잘못된 tab 값의 reason은 형식 오류 문구이고 내부 클래스 이름을 노출하지 않는다")
	void invalidTabReason(String tab) throws Exception {
		mockMvc.perform(get(URL).queryParam("role", "renter").queryParam("tab", tab)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("tab"))
			.andExpect(jsonPath("$.error.details[0].reason").value("형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("토큰이 없으면 401 AUTH_UNAUTHORIZED다")
	void unauthorized() throws Exception {
		mockMvc.perform(get(URL).queryParam("role", "renter"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("쿼리 수는 항목 수와 상관없다: 대여 3건(물품 3·제공자 2) 4개, 21건 page 0 5개(count), page 1 4개(count 생략), 빈 페이지 1개")
	void queryCount() throws Exception {
		// given: owner가 물품 2개, otherOwner가 물품 1개
		VerifiedMember otherOwner = fixture.createVerifiedMember();
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
		rentalFixture.createRental(createItem(owner), renter, RentalStatus.RESERVED, START, END);
		rentalFixture.createRental(createItem(otherOwner), renter, RentalStatus.RESERVED, START, END);

		// when, then
		assertThat(countQueries(request("UPCOMING"))).isEqualTo(4);
		createReserved(18);
		assertThat(countQueries(request("UPCOMING"))).isEqualTo(5);
		assertThat(countQueries(request("UPCOMING").queryParam("page", "1"))).isEqualTo(4);
		assertThat(countQueries(request("DONE"))).isEqualTo(1);
	}

	private MockHttpServletRequestBuilder request(String tab) {
		MockHttpServletRequestBuilder builder = get(URL).queryParam("role", "renter")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()));
		return tab == null ? builder : builder.queryParam("tab", tab);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request);
	}

	private List<Long> rentalIds(MockHttpServletRequestBuilder request) throws Exception {
		String body = perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(body, "$.data.items[*].rentalId");
		return ids.stream().map(Number::longValue).toList();
	}

	private boolean canWriteReviewOf(String body, Rental rental) {
		List<Boolean> values = JsonPath.read(body,
			"$.data.items[?(@.rentalId == " + rental.getId() + ")].canWriteReview");
		assertThat(values).hasSize(1);
		return values.getFirst();
	}

	// 요청 직전에 비워 픽스처 저장이 섞이지 않게 한다
	private long countQueries(MockHttpServletRequestBuilder request) throws Exception {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		perform(request).andExpect(status().isOk());
		return statistics.getPrepareStatementCount();
	}

	private Item createItem(VerifiedMember itemOwner) {
		return fixture.createItem(itemOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
	}

	private List<Rental> createReserved(int count) {
		List<Rental> rentals = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			rentals.add(rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END));
		}
		return rentals;
	}

	private Rental createCompleted(LocalDateTime completedAt, LocalDate start) {
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, start, END);
		jdbcTemplate.update("UPDATE rentals SET completed_at = ? WHERE id = ?", completedAt, rental.getId());
		return rental;
	}
}
