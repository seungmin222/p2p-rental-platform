package com.team7.rental.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.payment.dto.DepositRecommendation;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.repository.DamagePolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DamagePolicyQueryService {
	private final DamagePolicyRepository policies;

	/** 권장 비율은 등급 공통이며 A 행을 기준으로 읽는다. 원 미만은 절사한다. */
	public DepositRecommendation recommend(Long categoryId, int itemPrice) {
		var policy = policies.findByCategoryIdAndGrade(categoryId, DamageGrade.A)
			.orElseThrow(() -> new IllegalStateException("카테고리의 권장 보증금 정책이 없습니다."));
		var price = BigDecimal.valueOf(itemPrice);
		return new DepositRecommendation(price.multiply(policy.getRecommendedDepositMinRate())
			.setScale(0, RoundingMode.DOWN).intValueExact(),
			price.multiply(policy.getRecommendedDepositMaxRate()).setScale(0, RoundingMode.DOWN).intValueExact());
	}
}
