package com.team7.rental.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DamagePolicy;

public interface DamagePolicyRepository extends JpaRepository<DamagePolicy, Long> {
	Optional<DamagePolicy> findByCategoryIdAndGrade(Long categoryId, DamageGrade grade);
}
