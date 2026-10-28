package com.team7.rental.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.MemberRegion;

public interface MemberRegionRepository extends JpaRepository<MemberRegion, Long> {

	Optional<MemberRegion> findFirstByMemberIdAndIsPrimaryTrue(Long memberId);
}
