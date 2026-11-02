package com.team7.rental.payment.entity;

import java.math.BigDecimal;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "damage_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DamagePolicy extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private Long categoryId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DamageGrade grade;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DeductionType deductionType;
	private Integer fixedAmount;
	@Column(precision = 5, scale = 4)
	private BigDecimal capRate;
	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal recommendedDepositMinRate;
	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal recommendedDepositMaxRate;
}
