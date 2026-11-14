package com.team7.rental.review.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByRentalIdAndWriterId(Long rentalId, Long writerId);

	@Query("select r.rentalId from Review r where r.writerId = :writerId and r.rentalId in :rentalIds")
	List<Long> findRentalIdsByWriter(@Param("writerId")
	Long writerId,
		@Param("rentalIds")
		Collection<Long> rentalIds);

	/** 받은 후기. 정렬은 pageable(id DESC)이 정한다. 칭찬 항목은 {@link #findCompliments}로 따로 읽는다 */
	Page<Review> findByTargetId(Long targetId, Pageable pageable);

	/** @return [후기 ID, ComplimentType] 행. 칭찬 항목이 없는 후기는 행이 없다 */
	@Query("select r.id, c from Review r join r.compliments c where r.id in :reviewIds")
	List<Object[]> findCompliments(@Param("reviewIds")
	Collection<Long> reviewIds);
}
