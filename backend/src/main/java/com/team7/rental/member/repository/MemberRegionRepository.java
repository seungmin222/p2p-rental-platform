package com.team7.rental.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.MemberRegion;

public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long> {

	List<MemberRegion> findByMemberIdOrderByIsPrimaryDescIdAsc(Long memberId);

	Optional<MemberRegion> findByIdAndMemberId(Long id, Long memberId);

	Optional<MemberRegion> findFirstByMemberIdAndIsPrimaryTrue(Long memberId);
}
