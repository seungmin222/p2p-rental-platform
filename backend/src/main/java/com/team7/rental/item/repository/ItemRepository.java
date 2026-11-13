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

	/**
	 * 채팅방을 새로 만들 때 한 번 (REQ-026). 동시 생성에도 빠지지 않게 원자적으로 올린다. 삭제(DELETED)된 물품은 올리지 않는다(0 반환):
	 * 방 생성 전 삭제 확인과 이 UPDATE 사이에 삭제가 커밋되면 여기서 걸러 방이 생기지 않게 한다
	 */
	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("update Item i set i.chatCount = i.chatCount + 1 where i.id = :id "
		+ "and i.status <> com.team7.rental.item.entity.ItemStatus.DELETED")
	int increaseChatCount(@org.springframework.data.repository.query.Param("id")
	Long id);
}
