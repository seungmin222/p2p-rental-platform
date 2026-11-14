package com.team7.rental.item.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.team7.rental.item.entity.MyItemTab;
import com.team7.rental.member.dto.RegionCenter;

public interface ItemListingRepository {
	List<ItemListRow> findFeed(RegionCenter center, double radiusMeters, Long categoryId,
		ItemFeedCursor cursor, int limit, LocalDateTime now);

	/** 필터링 검색 (REQ-010). 위치 조건은 피드와 같다 */
	Page<ItemListRow> search(RegionCenter center, double radiusMeters, ItemSearchCondition condition,
		Pageable pageable, LocalDateTime now);

	Page<ItemListRow> findMyItems(Long memberId, MyItemTab tab, Pageable pageable, LocalDateTime now);
}
