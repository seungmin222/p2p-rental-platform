package com.team7.rental.payment.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementStatus;

import jakarta.persistence.LockModeType;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

	boolean existsByRentalId(Long rentalId);

	Optional<Settlement> findByRentalId(Long rentalId);

	/**
	 * 대여의 정산 id. 엔티티를 영속성 컨텍스트에 올리지 않아, 뒤이은 {@link #findByIdForUpdate}가 락 뒤의 최신 상태를 읽는다. 잠금
	 * 읽기(FOR SHARE)라 REPEATABLE READ 스냅샷이 대여 행 락보다 먼저 잡혔어도 락을 기다리는 동안 커밋된 정산을 본다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select s.id from Settlement s where s.rentalId = :rentalId")
	Optional<Long> findIdByRentalId(@Param("rentalId")
	Long rentalId);

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

	/** 내 정산 목록, 최신 생성 순 (idx_settlements_owner_id_id) */
	Page<Settlement> findByOwnerIdOrderByIdDesc(Long ownerId, Pageable pageable);

	/** 상태별 지급액 합계. 없으면 0 */
	@Query("select coalesce(sum(s.payoutAmount), 0) from Settlement s where s.ownerId = :ownerId and s.status = :status")
	long sumPayoutAmount(@Param("ownerId")
	Long ownerId, @Param("status")
	SettlementStatus status);
}
