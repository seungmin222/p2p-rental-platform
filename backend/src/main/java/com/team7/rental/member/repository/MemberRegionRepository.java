package com.team7.rental.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.MemberRegion;

public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long> {

	List<MemberRegion> findByMemberIdOrderByIsPrimaryDescIdAsc(Long memberId);

	Optional<MemberRegion> findByIdAndMemberId(Long id, Long memberId);

	Optional<MemberRegion> findFirstByMemberIdAndIsPrimaryTrue(Long memberId);

	/** 여러 회원의 대표 동네 (목록 화면용 일괄 조회) */
	List<MemberRegion> findByMemberIdInAndIsPrimaryTrue(java.util.Collection<Long> memberIds);
}
