package com.team7.rental.item.repository;

import java.time.LocalDate;

import com.team7.rental.item.entity.ItemSortType;

/**
 * 검색 조건 (REQ-010). 위치 조건은 따로 받는다.
 *
 * @param keyword 앞뒤 공백을 지운 검색어. 없으면 null
 * @param startDate 대여 희망 시작일. endDate와 함께 있거나 함께 null
 * @param availableOnly 기간이 있으면 기간 조건과 같으므로 기간이 없을 때만 의미가 있다
 */
public record ItemSearchCondition(String keyword, Long categoryId, Integer minPrice, Integer maxPrice,
	LocalDate startDate, LocalDate endDate, boolean availableOnly, ItemSortType sort) {

	public boolean hasPeriod() {
		return startDate != null && endDate != null;
	}
}
