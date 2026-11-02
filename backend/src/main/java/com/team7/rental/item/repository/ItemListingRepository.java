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

	Page<ItemListRow> findMyItems(Long memberId, MyItemTab tab, Pageable pageable, LocalDateTime now);
}
