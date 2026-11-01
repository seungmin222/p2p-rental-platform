package com.team7.rental.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {
	Optional<Region> findByCode(String code);

	List<Region> findByNameContainingOrderBySidoAscSigunguAscNameAsc(String keyword);
}
