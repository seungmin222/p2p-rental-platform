package com.team7.rental.rental.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.RentalCancelAgreement;

public interface RentalCancelAgreementRepository extends JpaRepository<RentalCancelAgreement, Long> {

	/**
	 * 진행 중인 요청(REQUESTED이고 expiresAt > now, {@link RentalCancelAgreement#isOpen}과 같은 조건).
	 * 만료 배치가 아직 돌지 않은 REQUESTED 행은 제외된다. 대여 행 락 안에서 만들므로 1건이지만, 혹시 여럿이면 최신 것.
	 */
	default Optional<RentalCancelAgreement> findOpen(Long rentalId, LocalDateTime now) {
		return findFirstByRentalIdAndStatusAndExpiresAtAfterOrderByIdDesc(rentalId, CancelAgreementStatus.REQUESTED,
			now);
	}

	Optional<RentalCancelAgreement> findFirstByRentalIdAndStatusAndExpiresAtAfterOrderByIdDesc(Long rentalId,
		CancelAgreementStatus status, LocalDateTime now);

	Optional<RentalCancelAgreement> findByIdAndRentalId(Long id, Long rentalId);

	/**
	 * 만료 시각이 지난(expiresAt ≤ now) REQUESTED를 EXPIRED로 바꾼다(만료 배치). 벌크라 auditing이 돌지 않아 updatedAt을 직접 넣는다.
	 * status = REQUESTED 조건으로 이미 응답한 행은 건드리지 않고, 두 번 실행해도 결과가 같다.
	 *
	 * @return 만료한 건수
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update RentalCancelAgreement a
		set a.status = com.team7.rental.rental.entity.CancelAgreementStatus.EXPIRED, a.updatedAt = :now
		where a.status = com.team7.rental.rental.entity.CancelAgreementStatus.REQUESTED and a.expiresAt <= :now
		""")
	int expireOverdue(@Param("now")
	LocalDateTime now);
}
