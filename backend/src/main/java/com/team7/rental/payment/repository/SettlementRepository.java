package com.team7.rental.payment.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.payment.entity.Settlement;

import jakarta.persistence.LockModeType;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

	boolean existsByRentalId(Long rentalId);

	Optional<Settlement> findByRentalId(Long rentalId);

	/** 지급 배치 대상 id: SCHEDULED이고 scheduledDate ≤ today. id 순 */
	@Query("""
		select s.id from Settlement s
		where s.status = com.team7.rental.payment.entity.SettlementStatus.SCHEDULED
			and s.scheduledDate <= :today
		order by s.id
		""")
	List<Long> findPayableIds(@Param("today")
	LocalDate today);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Settlement s where s.id = :id")
	Optional<Settlement> findByIdForUpdate(@Param("id")
	Long id);
}
