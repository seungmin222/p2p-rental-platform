package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.response.PageResponse;
import com.team7.rental.item.dto.ItemSearchRequest;
import com.team7.rental.item.dto.ItemSummaryResponse;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemAvailablePeriod;
import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.item.entity.ItemSortType;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;

/** 필터링 검색 (REQ-010) */
class ItemSearchIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private ItemQueryService queries;
	@Autowired
	private ItemRepository items;
	@Autowired
	private ItemAvailablePeriodRepository periods;
	@Autowired
	private CategoryRepository categories;
	@Autowired
	private RentalExtensionRepository extensions;
	private MemberItemFixture.VerifiedMember owner;
	private Member renter;
	private Long categoryId;
	private final LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
	private final LocalDate today = now.toLocalDate();

	@BeforeEach
	void setUp() {
		testClock.fixAt(now);
		owner = fixture.createVerifiedMember();
		renter = fixture.createMember();
		categoryId = categories.findAll().getFirst().getId();
	}

	@Test
	@DisplayName("정확도순은 이름 일치 > 설명만 일치 > 최신순이고 일치하지 않는 물품은 빠진다")
	void accuracyOrdersNameMatchBeforeDescriptionThenLatest() {
		var oldName = create("캠핑 텐트", "설명", 1000, now.minusDays(3));
		var newDescription = create("타프", "텐트와 같이 쓰는 타프", 1000, now.minusDays(1));
		var newName = create("원터치 텐트", "설명", 1000, now.minusDays(2));
		var chair = create("의자", "설명", 1000, now);

		assertThat(ids(search(builder().keyword("  텐트 ")))).containsExactly(newName.getId(), oldName.getId(),
			newDescription.getId());
		// 검색어가 없으면 최신순과 같다
		assertThat(ids(search(builder()))).containsExactly(chair.getId(), newDescription.getId(), newName.getId(),
			oldName.getId());
	}

	@Test
	@DisplayName("검색어의 %·_는 문자 그대로 찾는다")
	void keywordEscapesLikeWildcards() {
		var percent = create("면 100% 담요", "설명", 1000, now);
		create("면 담요", "설명", 1000, now);
		var underscore = create("a_b 케이블", "설명", 1000, now);
		create("axb 케이블", "설명", 1000, now);

		assertThat(ids(search(builder().keyword("100%")))).containsExactly(percent.getId());
		assertThat(ids(search(builder().keyword("a_b")))).containsExactly(underscore.getId());
	}

	@Test
	@DisplayName("최신순은 bumpedAt·id 내림차순, 가격순은 일 대여료 오름차순·id 내림차순이다")
	void latestAndPriceOrders() {
		var cheapOld = create("A", "설명", 1000, now.minusDays(2));
		var expensiveNew = create("B", "설명", 3000, now);
		var cheapNew1 = create("C", "설명", 1000, now);
		var cheapNew2 = create("D", "설명", 1000, now);

		assertThat(ids(search(builder().sort(ItemSortType.LATEST)))).containsExactly(cheapNew2.getId(),
			cheapNew1.getId(), expensiveNew.getId(), cheapOld.getId());
		assertThat(ids(search(builder().sort(ItemSortType.PRICE_ASC)))).containsExactly(cheapNew2.getId(),
			cheapNew1.getId(), cheapOld.getId(), expensiveNew.getId());
	}

	@Test
	@DisplayName("카테고리와 일 대여료 범위(양 끝 포함)로 거른다")
	void filtersCategoryAndPriceRange() {
		var low = create("A", "설명", 1000, now);
		var mid = create("B", "설명", 2000, now);
		var high = create("C", "설명", 3000, now);
		Long otherCategoryId = categories.findAll().stream().map(c -> c.getId()).filter(id -> !id.equals(categoryId))
			.findFirst().orElseThrow();
		Long originalCategoryId = categoryId;
		categoryId = otherCategoryId;
		var other = create("D", "설명", 2000, now);

		assertThat(ids(search(builder().minPrice(1000).maxPrice(2000).sort(ItemSortType.PRICE_ASC))))
			.containsExactly(low.getId(), other.getId(), mid.getId());
		assertThat(ids(search(builder().categoryId(originalCategoryId)))).containsExactlyInAnyOrder(low.getId(),
			mid.getId(), high.getId());
		assertThat(ids(search(builder().categoryId(otherCategoryId).minPrice(2500)))).isEmpty();
	}

	@Test
	@DisplayName("희망 기간은 가능 기간 하나에 모두 들어가고 점유 기간과 겹치지 않아야 한다(양 끝 포함)")
	void periodMustFitOneAvailablePeriodAndNotOverlapOccupancy() {
		LocalDate from = today.plusDays(1);
		LocalDate to = today.plusDays(10);
		var exactFit = createWithPeriods("경계 일치", period(from, to));
		createWithPeriods("시작일 밖", period(from.plusDays(1), to));
		createWithPeriods("두 기간에 걸침",
			period(from, from.plusDays(4)), period(from.plusDays(5), to));
		var reservedOnEnd = createWithPeriods("마지막 날 예약", period(from, to.plusDays(5)));
		rentalFixture.createRental(reservedOnEnd, renter, RentalStatus.RESERVED, to, to.plusDays(2));
		var ignored = createWithPeriods("무시할 대여만", period(today, to.plusDays(5)));
		rentalFixture.createRental(ignored, renter, RentalStatus.CANCELED, from, to);
		rentalFixture.createPendingPayment(ignored, renter, from, to, now);
		rentalFixture.createRental(ignored, renter, RentalStatus.COMPLETED, from, from);
		rentalFixture.createRental(ignored, renter, RentalStatus.RESERVED, to.plusDays(1), to.plusDays(2));
		var pending = createWithPeriods("결제 대기", period(from, to));
		rentalFixture.createPendingPayment(pending, renter, from, from, now.plusMinutes(1));
		var extending = createWithPeriods("결제 대기 연장", period(today.minusDays(3), to));
		Rental renting = rentalFixture.createRental(extending, renter, RentalStatus.RENTING, today.minusDays(3), today);
		extensions.saveAndFlush(RentalExtension.pendingPayment(renting.getId(), today, from, 1000, now.plusMinutes(1)));

		var result = search(builder().startDate(from).endDate(to).sort(ItemSortType.LATEST));
		assertThat(ids(result)).containsExactly(ignored.getId(), exactFit.getId());
		// availableOnly를 함께 보내도 기간 기준 그대로
		assertThat(ids(search(builder().startDate(from).endDate(to).availableOnly(true).sort(ItemSortType.LATEST))))
			.containsExactly(ignored.getId(), exactFit.getId());
	}

	@Test
	@DisplayName("기간 없는 대여 가능만 보기는 남은 가능 기간·오늘 점유 없음·대여 중 아님을 모두 만족해야 한다")
	void availableOnlyWithoutPeriod() {
		var futureOnly = createWithPeriods("앞으로 가능", period(today.plusDays(1), today.plusDays(5)));
		createWithPeriods("기간 끝남", period(today.minusDays(5), today.minusDays(1)));
		var reservedToday = createWithPeriods("오늘 예약", period(today, today.plusDays(5)));
		rentalFixture.createRental(reservedToday, renter, RentalStatus.RESERVED, today, today);
		var overdue = createWithPeriods("미반납 연체", period(today.minusDays(5), today.plusDays(5)));
		rentalFixture.createRental(overdue, renter, RentalStatus.OVERDUE, today.minusDays(4), today.minusDays(2));
		var reservedTomorrow = createWithPeriods("내일 예약", period(today, today.plusDays(5)));
		rentalFixture.createRental(reservedTomorrow, renter, RentalStatus.RESERVED, today.plusDays(1),
			today.plusDays(1));

		assertThat(ids(search(builder().availableOnly(true)))).containsExactlyInAnyOrder(futureOnly.getId(),
			reservedTomorrow.getId());
		assertThat(ids(search(builder().availableOnly(false)))).hasSize(5);
	}

	@Test
	@DisplayName("동네 범위 밖, 숨김·삭제 물품은 빠지고 regionId가 없으면 대표 동네를 쓴다")
	void rangeStatusAndDefaultRegion() {
		var visible = create("공개", "설명", 1000, now);
		create("5km 밖", "설명", 1000, now, 37.545, 127.03);
		setStatus(create("숨김", "설명", 1000, now), ItemStatus.HIDDEN);
		setStatus(create("삭제", "설명", 1000, now), ItemStatus.DELETED);

		assertThat(ids(search(builder()))).containsExactly(visible.getId());
		var byPrimary = queries.search(builder().regionId(null).build(), owner.member().getId());
		assertThat(ids(byPrimary)).containsExactly(visible.getId());
	}

	@Test
	@DisplayName("페이지 크기는 20건 고정이고 같은 시각끼리도 페이지 사이에 중복·누락이 없다")
	void pagesAreFixedAtTwentyWithStableOrder() {
		var expected = IntStream.range(0, 21).mapToObj(i -> create("물품" + i, "설명", 1000, now).getId())
			.sorted(Comparator.reverseOrder()).toList();
		var first = search(builder().sort(ItemSortType.LATEST));
		var second = search(builder().sort(ItemSortType.LATEST).page(1));
		assertThat(first.items()).hasSize(20);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.totalElements()).isEqualTo(21);
		assertThat(second.hasNext()).isFalse();
		assertThat(java.util.stream.Stream.concat(ids(first).stream(), ids(second).stream()).toList())
			.isEqualTo(expected);
	}

	@Test
	@DisplayName("기간 역전·가격 역전·비회원 regionId 생략은 400이다")
	void rejectsInvalidInputs() throws Exception {
		String regionId = owner.region().getId().toString();
		mockMvc.perform(get("/api/items").param("regionId", regionId).param("startDate", today.plusDays(3).toString())
			.param("endDate", today.plusDays(1).toString())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("endDate"));
		mockMvc
			.perform(get("/api/items").param("regionId", regionId).param("minPrice", "2000").param("maxPrice", "1000"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("minPrice"));
		mockMvc.perform(get("/api/items").param("keyword", "텐트")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("regionId"));
		mockMvc.perform(get("/api/items").param("regionId", regionId).param("keyword", "가".repeat(500)))
			.andExpect(status().isOk());
	}

	private PageResponse<ItemSummaryResponse> search(Builder builder) {
		return queries.search(builder.build(), null);
	}

	private static List<Long> ids(PageResponse<ItemSummaryResponse> page) {
		return page.items().stream().map(ItemSummaryResponse::itemId).toList();
	}

	private Builder builder() {
		return new Builder().regionId(owner.region().getId());
	}

	private Item create(String name, String description, int pricePerDay, LocalDateTime bumpedAt) {
		return create(name, description, pricePerDay, bumpedAt, 37.5, 127.03);
	}

	private Item create(String name, String description, int pricePerDay, LocalDateTime bumpedAt, double lat,
		double lng) {
		return items.saveAndFlush(Item.create(owner.member().getId(), owner.region().getId(), categoryId, name,
			description, ItemCondition.LIKE_NEW, pricePerDay, 1000, 10000, "주소", lat, lng, bumpedAt));
	}

	private Item createWithPeriods(String name, Period... availablePeriods) {
		Item item = create(name, "설명", 1000, now);
		for (Period p : availablePeriods) {
			periods.saveAndFlush(ItemAvailablePeriod.create(item.getId(), p.start(), p.end()));
		}
		return item;
	}

	private static Period period(LocalDate start, LocalDate end) {
		return new Period(start, end);
	}

	private record Period(LocalDate start, LocalDate end) {
	}

	private void setStatus(Item item, ItemStatus status) {
		ReflectionTestUtils.setField(item, "status", status);
		items.saveAndFlush(item);
	}

	private static class Builder {
		private Long regionId;
		private String keyword;
		private Long categoryId;
		private Integer minPrice;
		private Integer maxPrice;
		private LocalDate startDate;
		private LocalDate endDate;
		private Boolean availableOnly;
		private ItemSortType sort;
		private Integer page;

		Builder regionId(Long value) {
			regionId = value;
			return this;
		}

		Builder keyword(String value) {
			keyword = value;
			return this;
		}

		Builder categoryId(Long value) {
			categoryId = value;
			return this;
		}

		Builder minPrice(Integer value) {
			minPrice = value;
			return this;
		}

		Builder maxPrice(Integer value) {
			maxPrice = value;
			return this;
		}

		Builder startDate(LocalDate value) {
			startDate = value;
			return this;
		}

		Builder endDate(LocalDate value) {
			endDate = value;
			return this;
		}

		Builder availableOnly(Boolean value) {
			availableOnly = value;
			return this;
		}

		Builder sort(ItemSortType value) {
			sort = value;
			return this;
		}

		Builder page(Integer value) {
			page = value;
			return this;
		}

		ItemSearchRequest build() {
			return new ItemSearchRequest(regionId, null, keyword, categoryId, minPrice, maxPrice, startDate, endDate,
				availableOnly, sort, page);
		}
	}
}
