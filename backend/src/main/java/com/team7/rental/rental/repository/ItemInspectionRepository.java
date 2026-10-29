package com.team7.rental.rental.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;

public interface ItemInspectionRepository extends JpaRepository<ItemInspection, Long> {

	Optional<ItemInspection> findByRentalIdAndPhase(Long rentalId, InspectionPhase phase);

	/** HANDOVER → RETURN 순 (enum 이름 사전순이 아니므로 id 순. 인계가 반납보다 먼저 저장된다) */
	List<ItemInspection> findAllByRentalIdOrderById(Long rentalId);
}
