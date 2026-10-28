package com.team7.rental.item.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);

	@Test
	@DisplayName("소유자 id면 true, 다른 회원이나 null이면 false")
	void isOwnedBy() {
		// given
		Item item = createItem(7L);

		// then
		assertThat(item.isOwnedBy(7L)).isTrue();
		assertThat(item.isOwnedBy(8L)).isFalse();
		assertThat(item.isOwnedBy(null)).isFalse();
	}

	@Test
	@DisplayName("등록하면 AVAILABLE이고 카운터는 0, bumpedAt은 등록 시각이다")
	void createDefaults() {
		// when
		Item item = createItem(7L);

		// then
		assertThat(item.getStatus()).isEqualTo(ItemStatus.AVAILABLE);
		assertThat(item.getViewCount()).isZero();
		assertThat(item.getWishCount()).isZero();
		assertThat(item.getChatCount()).isZero();
		assertThat(item.getBumpedAt()).isEqualTo(NOW);
	}

	private Item createItem(Long ownerId) {
		return Item.create(ownerId, 1L, 1L, "캠핑 의자", "설명", ItemCondition.LIKE_NEW, 10_000, 50_000, 200_000,
			"서울 강남구 테헤란로 152", 37.5, 127.0, NOW);
	}
}
