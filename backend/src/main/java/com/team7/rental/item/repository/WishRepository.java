package com.team7.rental.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.item.entity.Wish;

public interface WishRepository extends JpaRepository<Wish, Long> {

	boolean existsByMemberIdAndItemId(Long memberId, Long itemId);

	/** @return 지운 행 수(0 또는 1) */
	@Modifying
	@Query("delete from Wish w where w.memberId = :memberId and w.itemId = :itemId")
	int deleteByMemberIdAndItemId(@Param("memberId")
	Long memberId, @Param("itemId")
	Long itemId);
}
