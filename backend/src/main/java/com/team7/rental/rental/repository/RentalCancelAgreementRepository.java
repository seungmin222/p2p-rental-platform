package com.team7.rental.rental.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
