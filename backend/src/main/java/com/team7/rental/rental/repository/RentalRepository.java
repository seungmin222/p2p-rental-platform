package com.team7.rental.rental.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

public interface RentalRepository extends JpaRepository<Rental, Long> {

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
