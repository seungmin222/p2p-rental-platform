package com.team7.rental.item.repository;

import java.time.LocalDateTime;

import com.team7.rental.item.entity.ItemStatus;

/** 목록용 컬럼만 조회한다. 비공개 물품 가격은 포함하지 않는다. */
public record ItemListRow(Long itemId, Long regionId, String name, int pricePerDay, int deposit,
	ItemStatus status, int viewCount, int wishCount, int chatCount, LocalDateTime bumpedAt,
	boolean renting, long upcomingCount) {
}
