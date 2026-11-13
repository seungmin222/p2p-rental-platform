package com.team7.rental.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.Item;

public interface ItemRepository extends JpaRepository<Item, Long>, ItemListingRepository {
	/** 누적 스냅샷을 반영해 재시도·중복 배치에서도 조회수가 중복 증가하지 않는다. */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.viewCount = :count where i.id = :id and i.viewCount < :count")
	int advanceViewCount(@org.springframework.data.repository.query.Param("id")
	Long id,
		@org.springframework.data.repository.query.Param("count")
		int count);

	/** 채팅방을 새로 만들 때 한 번 (REQ-026). 동시 생성에도 빠지지 않게 원자적으로 올린다 */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.chatCount = i.chatCount + 1 where i.id = :id")
	int increaseChatCount(@org.springframework.data.repository.query.Param("id")
	Long id);
}
