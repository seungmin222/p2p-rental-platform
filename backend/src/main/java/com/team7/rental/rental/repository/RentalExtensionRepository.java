package com.team7.rental.rental.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.rental.entity.RentalExtension;

import jakarta.persistence.LockModeType;

public interface RentalExtensionRepository extends JpaRepository<RentalExtension, Long> {

	/**
	 * 행 락으로 읽는다. 호출하는 쪽이 대여 행 락을 먼저 잡는다(대여 → 연장 → 결제 순서, notes.md "락 순서").
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from RentalExtension e where e.id = :id")
	Optional<RentalExtension> findByIdForUpdate(@Param("id")
	Long id);

	/** 결제(주문)에 묶인 연장. 대여 행 락 뒤에 부른다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from RentalExtension e where e.paymentId = :paymentId")
	Optional<RentalExtension> findByPaymentIdForUpdate(@Param("paymentId")
	Long paymentId);

	/** 이 대여에 만료 전 결제 대기 연장이 있는지 (대여당 1건 제한, 연체 배치 건너뛰기) */
	@Query("""
		select count(e) > 0 from RentalExtension e
		where e.rentalId = :rentalId
			and e.status = com.team7.rental.rental.entity.ExtensionStatus.PENDING_PAYMENT
			and e.expiresAt > :now
		""")
	boolean existsPending(@Param("rentalId")
	Long rentalId, @Param("now")
	LocalDateTime now);

	/**
	 * 물품의 [from, to]와 겹치는 만료 전 결제 대기 연장. 연장 기간은 [prevEndDate + 1, newEndDate]라 시작 조건은 prevEndDate &lt; to다.
	 * 직접 부르지 말고 RentalOccupancyReader를 쓴다.
	 */
	@Query("""
		select e from RentalExtension e, Rental r
		where r.id = e.rentalId
			and r.itemId = :itemId
			and e.status = com.team7.rental.rental.entity.ExtensionStatus.PENDING_PAYMENT
			and e.expiresAt > :now
			and e.prevEndDate < :to and e.newEndDate >= :from
		order by e.prevEndDate
		""")
	List<RentalExtension> findPendingOccupying(@Param("itemId")
	Long itemId, @Param("from")
	LocalDate from, @Param("to")
	LocalDate to, @Param("now")
	LocalDateTime now);

	/** 연장 결제 만료 배치 대상: PENDING_PAYMENT이고 expiresAt ≤ now. id 순. 인덱스 (status, expires_at) */
	@Query("""
		select new com.team7.rental.rental.repository.ExtensionRef(e.id, e.rentalId) from RentalExtension e
		where e.status = com.team7.rental.rental.entity.ExtensionStatus.PENDING_PAYMENT
			and e.expiresAt <= :now
		order by e.id
		""")
	List<ExtensionRef> findExpiredRefs(@Param("now")
	LocalDateTime now);
}
