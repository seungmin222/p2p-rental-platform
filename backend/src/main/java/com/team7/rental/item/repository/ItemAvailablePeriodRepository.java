package com.team7.rental.item.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.ItemAvailablePeriod;

public interface ItemAvailablePeriodRepository extends JpaRepository<ItemAvailablePeriod, Long> {

	List<ItemAvailablePeriod> findAllByItemIdOrderByStartDate(Long itemId);

	/** 수정(REQ-007) 시 기간 전체 교체 */
	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = false)
	@org.springframework.data.jpa.repository.Query("delete from ItemAvailablePeriod p where p.itemId = :itemId")
	int deleteAllByItemId(@org.springframework.data.repository.query.Param("itemId")
	Long itemId);
}
