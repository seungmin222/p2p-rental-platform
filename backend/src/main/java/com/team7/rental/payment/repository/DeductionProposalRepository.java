package com.team7.rental.payment.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.payment.entity.DeductionProposal;
import com.team7.rental.payment.entity.DeductionProposalStatus;

/** 쓰기는 대여 행 락 → 결제 행 락을 잡은 뒤 읽는다(같은 대여의 요청은 이미 한 줄로 서 있어 제안 행은 잠그지 않는다) */
public interface DeductionProposalRepository extends JpaRepository<DeductionProposal, Long> {

	/** 대여의 최신 제안 (상태 무관) */
	Optional<DeductionProposal> findFirstByRentalIdOrderByIdDesc(Long rentalId);

	boolean existsByRentalIdAndStatus(Long rentalId, DeductionProposalStatus status);

	/** 자동 수락 대상: PROPOSED이고 createdAt ≤ threshold(now − deduction.renter-response-hours). id 순 */
	@Query("""
		select new com.team7.rental.payment.repository.DeductionAutoAcceptTarget(p.id, p.rentalId)
		from DeductionProposal p
		where p.status = com.team7.rental.payment.entity.DeductionProposalStatus.PROPOSED
			and p.createdAt <= :threshold
		order by p.id
		""")
	List<DeductionAutoAcceptTarget> findAutoAcceptTargets(@Param("threshold")
	LocalDateTime threshold);

	/** 대여별 최신 제안 (보류 목록용) */
	@Query("""
		select p from DeductionProposal p
		where p.id in (select max(q.id) from DeductionProposal q where q.rentalId in :rentalIds group by q.rentalId)
		""")
	List<DeductionProposal> findLatestByRentalIds(@Param("rentalIds")
	Collection<Long> rentalIds);
}
