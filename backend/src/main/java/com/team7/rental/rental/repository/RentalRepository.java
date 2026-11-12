package com.team7.rental.rental.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

import jakarta.persistence.LockModeType;

public interface RentalRepository extends JpaRepository<Rental, Long>, RentalSearchRepository {

	/**
	 * 행 락(SELECT ... FOR UPDATE)으로 읽는다. 취소·동의 요청·동의 응답이 같은 대여에서 한 줄로 서게 한다(REQ-015).
	 * 락 대기 시간 힌트는 두지 않는다(MySQL innodb_lock_wait_timeout 기본값). 이유는 docs/prompts/feat/rental-cancel/notes.md
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Rental r where r.id = :id")
	Optional<Rental> findByIdForUpdate(@Param("id")
	Long id);

	/**
	 * 자동 상태 확인(REQ-018) 대상 id: RETURNED이고 returnedAt ≤ threshold(= now - return.auto-confirm-days),
	 * 반납 기록이 판정 전이고 미반납 신고가 아닌 것. id 순.
	 */
	@Query("""
		select r.id from Rental r, ItemInspection i
		where i.rentalId = r.id
			and i.phase = com.team7.rental.rental.entity.InspectionPhase.RETURN
			and r.status = com.team7.rental.rental.entity.RentalStatus.RETURNED
			and r.returnedAt <= :threshold
			and i.result is null
			and i.lostReported = false
		order by r.id
		""")
	List<Long> findAutoConfirmTargetIds(@Param("threshold")
	LocalDateTime threshold);

	/** 인계 없음 자동 취소(REQ-017) 대상 id: RESERVED이고 startDate &lt; threshold(= 오늘 - handover.auto-cancel-days). id 순 */
	@Query("""
		select r.id from Rental r
		where r.status = com.team7.rental.rental.entity.RentalStatus.RESERVED
			and r.startDate < :threshold
		order by r.id
		""")
	List<Long> findNoHandoverCancelIds(@Param("threshold")
	LocalDate threshold);

	/** 연체 배치(REQ-023) 대상 id: RENTING이고 endDate &lt; today. id 순. 인덱스 (status, end_date) */
	@Query("""
		select r.id from Rental r
		where r.status = com.team7.rental.rental.entity.RentalStatus.RENTING
			and r.endDate < :today
		order by r.id
		""")
	List<Long> findOverdueTargetIds(@Param("today")
	LocalDate today);

	/**
	 * 인계 리마인더(REQ-017) 대상: RESERVED이고 from &lt;= startDate &lt; to. id 순.
	 * to = 오늘(시작일이 지났는데 인계 없음), from = 오늘 - handover.auto-cancel-days(같은 회차에 자동 취소되는 건 제외)
	 */
	@Query("""
		select r from Rental r
		where r.status = com.team7.rental.rental.entity.RentalStatus.RESERVED
			and r.startDate >= :from and r.startDate < :to
		order by r.id
		""")
	List<Rental> findHandoverReminderTargets(@Param("from")
	LocalDate from, @Param("to")
	LocalDate to);

	/** 반납 리마인더(REQ-031) 대상: status이고 endDate가 그날인 대여. id 순. 인덱스 (status, end_date) */
	List<Rental> findAllByStatusAndEndDateOrderById(RentalStatus status, LocalDate endDate);

	/** 결제 만료(REQ-024) 대상 id: PENDING_PAYMENT이고 paymentExpiresAt ≤ now. id 순 */
	@Query("""
		select r.id from Rental r
		where r.status = com.team7.rental.rental.entity.RentalStatus.PENDING_PAYMENT
			and r.paymentExpiresAt <= :now
		order by r.id
		""")
	List<Long> findPaymentExpiredIds(@Param("now")
	LocalDateTime now);

	/**
	 * [from, to]와 겹치는(양 끝 포함) 기간 점유 대여. 상태는 {@link RentalStatus#ACTIVE_FOR_PERIOD}를 넘기고,
	 * PENDING_PAYMENT는 paymentExpiresAt > now인 것만 점유한다({@link Rental#isOccupyingPeriod}와 같은 조건).
	 * 직접 부르지 말고 RentalOccupancyReader를 쓴다.
	 */
	@Query("""
		select r from Rental r
		where r.itemId = :itemId
			and r.status in :statuses
			and r.startDate <= :to and r.endDate >= :from
			and (r.status <> com.team7.rental.rental.entity.RentalStatus.PENDING_PAYMENT or r.paymentExpiresAt > :now)
		order by r.startDate
		""")
	List<Rental> findOccupying(@Param("itemId")
	Long itemId, @Param("statuses")
	Collection<RentalStatus> statuses,
		@Param("from")
		LocalDate from, @Param("to")
		LocalDate to, @Param("now")
		LocalDateTime now);
}
