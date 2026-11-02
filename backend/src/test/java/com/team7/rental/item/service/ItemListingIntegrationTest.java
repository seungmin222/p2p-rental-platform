package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.dto.ItemFeedRequest;
import com.team7.rental.item.dto.MyItemRequest;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.entity.MyItemTab;
import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.RegionRange;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.service.RentalHistoryService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;

class ItemListingIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private ItemQueryService queries;
	@Autowired
	private ItemRepository items;
	@Autowired
	private CategoryRepository categories;
	@Autowired
	private RentalHistoryService history;
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
	void feedUsesTwentyItemCursorWithoutDuplicatesAtSameTimestamp() {
		var ids = IntStream.range(0, 23).mapToObj(i -> create("물품" + i, 37.5, 127.03).getId())
			.sorted(java.util.Comparator.reverseOrder()).toList();
		var first = queries.getFeed(feed(null), null);
		assertThat(first.items()).hasSize(20);
		assertThat(first.hasNext()).isTrue();
		assertThat(first.items().stream().map(v -> v.itemId()).toList()).isEqualTo(ids.subList(0, 20));
		var second = queries.getFeed(feed(first.nextCursor()), null);
		assertThat(second.items().stream().map(v -> v.itemId()).toList()).isEqualTo(ids.subList(20, 23));
		assertThat(second.hasNext()).isFalse();
		assertThat(second.nextCursor()).isNull();
	}

	@Test
	void feedUsesTradeCoordinatesAndExactThreeKmFilter() {
		double delta2999 = Math.toDegrees(2999.0 / 6370986.0);
		double delta3001 = Math.toDegrees(3001.0 / 6370986.0);
		var inside = create("범위 안", 37.5 + delta2999, 127.03);
		create("범위 밖", 37.5 + delta3001, 127.03);
		// bounding box 안이지만 실제 거리는 3km 밖인 대각선.
		create("대각선 밖", 37.52, 127.06);
		assertThat(queries.getFeed(feed(null), null).items()).extracting(v -> v.itemId())
			.containsExactly(inside.getId());
	}

	@Test
	void feedExcludesHiddenDeletedAndFiltersCategory() {
		var visible = create("공개", 37.5, 127.03);
		setStatus(create("숨김", 37.5, 127.03), ItemStatus.HIDDEN);
		setStatus(create("삭제", 37.5, 127.03), ItemStatus.DELETED);
		var otherCategory = categories.findAll().stream().filter(c -> !c.getId().equals(categoryId)).findFirst()
			.orElseThrow();
		categoryId = otherCategory.getId();
		create("다른 분류", 37.5, 127.03);
		var request = new ItemFeedRequest(owner.region().getId(), RegionRange.NORMAL, visible.getCategoryId(), null);
		assertThat(queries.getFeed(request, null).items()).extracting(v -> v.itemId()).containsExactly(visible.getId());
	}

	@Test
	void feedDefaultsToPrimaryRegionAndAlwaysNormalRangeForNow() {
		var near = create("2km", 37.518, 127.03);
		create("5km", 37.545, 127.03);
		for (RegionRange range : RegionRange.values()) {
			var result = queries.getFeed(new ItemFeedRequest(null, range, null, null), owner.member().getId());
			assertThat(result.items()).extracting(v -> v.itemId()).containsExactly(near.getId());
		}
	}

	@Test
	void feedRejectsMissingUnknownRegionAndMalformedCursor() throws Exception {
		mockMvc.perform(get("/api/items/feed")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("regionId"));
		mockMvc.perform(get("/api/items/feed").param("regionId", Long.toString(Long.MAX_VALUE)))
			.andExpect(status().isBadRequest());
		mockMvc
			.perform(
				get("/api/items/feed").param("regionId", owner.region().getId().toString()).param("cursor", "garbage"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("cursor"));
	}

	@Test
	void rentingBadgeAndMyItemTabsUseRentingAndOverdueOnly() {
		var available = create("예약만", 37.5, 127.03);
		var renting = create("대여중", 37.5, 127.03);
		var overdue = create("연체", 37.5, 127.03);
		rentalFixture.createRental(available, renter, RentalStatus.RESERVED, today, today.plusDays(1));
		rentalFixture.createRental(renting, renter, RentalStatus.RENTING, today, today.plusDays(1));
		rentalFixture.createRental(overdue, renter, RentalStatus.OVERDUE, today.minusDays(2), today.minusDays(1));
		assertThat(queries.getMyItems(owner.member().getId(), new MyItemRequest(MyItemTab.AVAILABLE, 0)).items())
			.extracting(v -> v.itemId()).containsExactly(available.getId());
		assertThat(queries.getMyItems(owner.member().getId(), new MyItemRequest(MyItemTab.RENTING, 0)).items())
			.extracting(v -> v.itemId()).containsExactly(overdue.getId(), renting.getId());
		assertThat(queries.getFeed(feed(null), null).items()).filteredOn(v -> v.isRenting())
			.extracting(v -> v.itemId()).containsExactly(overdue.getId(), renting.getId());
	}

	@Test
	void upcomingCountExcludesExpiredPaymentsAndPastDates() {
		var item = create("예약 건수", 37.5, 127.03);
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, today, today.plusDays(1));
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, today.minusDays(2), today.minusDays(1));
		rentalFixture.createPendingPayment(item, renter, today.plusDays(2), today.plusDays(3), now.plusMinutes(1));
		rentalFixture.createPendingPayment(item, renter, today.plusDays(4), today.plusDays(5), now);
		rentalFixture.createRental(item, renter, RentalStatus.CANCELED, today.plusDays(6), today.plusDays(7));
		assertThat(queries.getMyItems(owner.member().getId(), new MyItemRequest(null, null)).items().getFirst()
			.upcomingRentalCount()).isEqualTo(2);
	}

	@Test
	void myItemsAreOwnerOnlyExcludeDeletedAndReturnEmptyHiddenTab() throws Exception {
		var visible = create("내 물품", 37.5, 127.03);
		setStatus(create("삭제", 37.5, 127.03), ItemStatus.DELETED);
		setStatus(create("숨김", 37.5, 127.03), ItemStatus.HIDDEN);
		fixture.createItem(fixture.createVerifiedMember(), today, today.plusDays(1));
		var own = queries.getMyItems(owner.member().getId(), new MyItemRequest(null, null));
		assertThat(own.items()).extracting(v -> v.itemId()).containsExactly(visible.getId());
		assertThat(own.totalElements()).isEqualTo(1);
		assertThat(queries.getMyItems(owner.member().getId(), new MyItemRequest(MyItemTab.HIDDEN, 0)).items())
			.isEmpty();
		mockMvc.perform(get("/api/members/me/items")).andExpect(status().isUnauthorized());
		mockMvc
			.perform(get("/api/members/me/items").header(HttpHeaders.AUTHORIZATION, bearerToken(owner.member().getId()))
				.param("page", "-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rentalHistoryOrdersActiveAscendingThenPastDescending() {
		var item = create("현황", 37.5, 127.03);
		var reserved = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, today.plusDays(2),
			today.plusDays(3));
		var current = rentalFixture.createRental(item, renter, RentalStatus.RENTING, today, today.plusDays(1));
		var old = rentalFixture.createRental(item, renter, RentalStatus.COMPLETED, today.minusDays(6),
			today.minusDays(5));
		var recent = rentalFixture.createRental(item, renter, RentalStatus.CANCELED, today.minusDays(3),
			today.minusDays(2));
		var result = history.getItemRentals(item.getId(), owner.member().getId(), 0);
		assertThat(result.items()).extracting(v -> v.rentalId()).containsExactly(current.getId(), reserved.getId(),
			recent.getId(), old.getId());
		assertThat(result.items().getFirst().renter().nickname()).isEqualTo(renter.getNickname());
	}

	@Test
	void rentalHistoryEnforcesOwnerAndItemExistence() throws Exception {
		var item = create("권한", 37.5, 127.03);
		String path = "/api/items/" + item.getId() + "/rentals";
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_OWNER"));
		setStatus(item, ItemStatus.HIDDEN);
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearerToken(owner.member().getId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		setStatus(item, ItemStatus.DELETED);
		mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearerToken(owner.member().getId())))
			.andExpect(status().isNotFound());
	}

	private ItemFeedRequest feed(String cursor) {
		return new ItemFeedRequest(owner.region().getId(), null, null, cursor);
	}

	private Item create(String name, double lat, double lng) {
		return items.saveAndFlush(Item.create(owner.member().getId(), owner.region().getId(), categoryId, name,
			"설명", ItemCondition.LIKE_NEW, 1000, 1000, 10000, "주소", lat, lng, now));
	}

	private void setStatus(Item item, ItemStatus status) {
		ReflectionTestUtils.setField(item, "status", status);
		items.saveAndFlush(item);
	}
}
