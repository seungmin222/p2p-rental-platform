package com.team7.rental.item.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.item.entity.ItemImage;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

	/** 대표 이미지(첫 장) */
	Optional<ItemImage> findFirstByItemIdOrderBySortOrder(Long itemId);

	/**
	 * 물품별 대표 이미지(sort_order 최소). (item_id, sort_order)에 유니크 제약이 없어 한 물품에 여러 행이 나올 수 있으므로
	 * id 순으로 돌려주고, 호출하는 쪽이 물품별 첫 행을 쓴다.
	 */
	@Query("""
		select i from ItemImage i
		where i.itemId in :itemIds
			and i.sortOrder = (select min(i2.sortOrder) from ItemImage i2 where i2.itemId = i.itemId)
		order by i.id
		""")
	List<ItemImage> findMainImages(@Param("itemIds")
	Collection<Long> itemIds);
}
