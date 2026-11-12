package com.team7.rental.admin.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

import jakarta.persistence.LockModeType;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

	/** 내 문의 목록, 최신 접수 순 (idx_inquiries_member_id_id) */
	Page<Inquiry> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

	/** 이 회원이 이 대여에 아직 끝나지 않은 분쟁을 가졌는지 (중복 접수 검사. 대여 행 락 안에서 부른다) */
	boolean existsByRentalIdAndMemberIdAndTypeAndStatusNot(Long rentalId, Long memberId, InquiryType type,
		InquiryStatus status);

	/**
	 * 이 대여의 끝나지 않은 분쟁 id (정산 생성 시 보류 판단, idx_inquiries_rental_id_status). 잠금 읽기(FOR SHARE)라
	 * REPEATABLE READ 스냅샷이 대여 행 락보다 먼저 잡힌 트랜잭션에서도, 락을 기다리는 동안 커밋된 분쟁을 본다.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("""
		select i.id from Inquiry i
		where i.rentalId = :rentalId
			and i.type = com.team7.rental.admin.entity.InquiryType.DISPUTE
			and i.status <> com.team7.rental.admin.entity.InquiryStatus.RESOLVED
		""")
	List<Long> findOpenDisputeIdsForShare(@Param("rentalId")
	Long rentalId);
}
