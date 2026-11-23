package com.team7.rental.item.repository;

import java.time.LocalDateTime;

import com.team7.rental.item.entity.ItemStatus;

/** 내 관심 목록 한 줄 (REQ-022). 삭제·숨김 물품도 담는다. 비공개 물품 가격은 포함하지 않는다 */
public record WishListRow(Long itemId, Long regionId, String name, int pricePerDay, int deposit,
	ItemStatus status, int wishCount, int chatCount, boolean renting, LocalDateTime wishedAt) {
}
