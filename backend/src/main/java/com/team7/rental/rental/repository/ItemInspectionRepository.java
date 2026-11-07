package com.team7.rental.rental.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;

public interface ItemInspectionRepository extends JpaRepository<ItemInspection, Long> {

	Optional<ItemInspection> findByRentalIdAndPhase(Long rentalId, InspectionPhase phase);

	/** HANDOVER → RETURN 순 (enum 이름 사전순이 아니므로 id 순. 인계가 반납보다 먼저 저장된다) */
	List<ItemInspection> findAllByRentalIdOrderById(Long rentalId);

	/** 주어진 대여 중 반납 기록이 미반납 신고인 것 */
	@Query("select i.rentalId from ItemInspection i where i.rentalId in :rentalIds and i.lostReported = true")
	Set<Long> findLostReportedRentalIds(@Param("rentalIds")
	Collection<Long> rentalIds);
}
