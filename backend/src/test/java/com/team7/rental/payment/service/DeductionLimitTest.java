package com.team7.rental.payment.service;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DamagePolicy;
import com.team7.rental.payment.entity.DeductionType;

/** 차감 상한 (policies.md §4 계산 규칙). 보증금 100,000원 기준 */
class DeductionLimitTest {

	private static final int DEPOSIT = 100_000;

	@Test
	@DisplayName("B 정액은 정액이 상한이고, 남은 보증금이 더 작으면 남은 보증금까지다")
	void fixed() {
		DamagePolicy policy = policy(DamageGrade.B, DeductionType.FIXED, 10_000, null);

		DeductionLimit limit = DeductionLimit.of(policy, DEPOSIT, 0, false);
		DeductionLimit squeezed = DeductionLimit.of(policy, DEPOSIT, 95_000, false);

		assertThat(limit.maxAmount()).isEqualTo(10_000);
		assertThat(limit.receiptRequired()).isFalse();
		assertThat(squeezed.remainingDeposit()).isEqualTo(5_000);
		assertThat(squeezed.maxAmount()).isEqualTo(5_000);
	}

	@Test
	@DisplayName("공구 B 실비는 남은 보증금이 상한이고 구매 내역이 필요하다")
	void actualWithoutCap() {
		DeductionLimit limit = DeductionLimit.of(policy(DamageGrade.B, DeductionType.ACTUAL, null, null), DEPOSIT,
			30_000, false);

		assertThat(limit.cap()).isEqualTo(70_000);
		assertThat(limit.maxAmount()).isEqualTo(70_000);
		assertThat(limit.receiptRequired()).isTrue();
	}

	@Test
	@DisplayName("C는 floor(보증금 × 상한율)이고, 상한율은 연체료를 빼기 전 보증금에 곱한다")
	void actualWithCap() {
		DamagePolicy policy = policy(DamageGrade.C, DeductionType.ACTUAL, null, new BigDecimal("0.7000"));

		DeductionLimit limit = DeductionLimit.of(policy, 33_333, 0, false);
		DeductionLimit squeezed = DeductionLimit.of(policy, DEPOSIT, 40_000, false);

		assertThat(limit.cap()).isEqualTo(23_333);
		assertThat(squeezed.cap()).isEqualTo(70_000);
		assertThat(squeezed.maxAmount()).isEqualTo(60_000);
		assertThat(squeezed.receiptRequired()).isTrue();
	}

	@Test
	@DisplayName("D는 남은 보증금 전액이고, 연체료가 보증금 전액이면 0원이다")
	void full() {
		DamagePolicy policy = policy(DamageGrade.D, DeductionType.FULL, null, null);

		assertThat(DeductionLimit.of(policy, DEPOSIT, 15_000, false).maxAmount()).isEqualTo(85_000);
		assertThat(DeductionLimit.of(policy, DEPOSIT, DEPOSIT, true).maxAmount()).isZero();
	}

	@Test
	@DisplayName("A 등급과, 미반납 신고 건의 D 아닌 등급은 고를 수 없다")
	void gradeNotAllowed() {
		assertThatThrownBy(() -> DeductionLimit.of(policy(DamageGrade.A, DeductionType.NONE, 0, null), DEPOSIT, 0,
			false)).isInstanceOf(BusinessException.class).extracting("errorCode")
			.isEqualTo(ErrorCode.DEDUCTION_GRADE_NOT_ALLOWED);
		assertThatThrownBy(() -> DeductionLimit.of(policy(DamageGrade.C, DeductionType.ACTUAL, null,
			new BigDecimal("0.5000")), DEPOSIT, 0, true)).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.DEDUCTION_GRADE_NOT_ALLOWED);
	}

	@Test
	@DisplayName("금액 검사: 남은 보증금 초과가 먼저, 그다음 상한 초과(details에 maxAmount), 경계값은 통과")
	void validate() {
		DeductionLimit limit = DeductionLimit.of(policy(DamageGrade.C, DeductionType.ACTUAL, null,
			new BigDecimal("0.5000")), DEPOSIT, 60_000, false);

		assertThatCode(() -> limit.validate(0)).doesNotThrowAnyException();
		assertThatCode(() -> limit.validate(40_000)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limit.validate(40_001)).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.DEDUCTION_EXCEEDS_DEPOSIT);

		DeductionLimit capped = DeductionLimit.of(policy(DamageGrade.C, DeductionType.ACTUAL, null,
			new BigDecimal("0.5000")), DEPOSIT, 0, false);
		assertThatThrownBy(() -> capped.validate(50_001)).isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException exception = (BusinessException)e;
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEDUCTION_EXCEEDS_CAP);
				assertThat(exception.getDetails()).containsExactly(new FieldErrorDetail("maxAmount", "50000"));
			});
		assertThat(capped.refundAmount(50_000)).isEqualTo(50_000);
	}

	private static DamagePolicy policy(DamageGrade grade, DeductionType type, Integer fixedAmount,
		BigDecimal capRate) {
		DamagePolicy policy = BeanUtils.instantiateClass(DamagePolicy.class);
		ReflectionTestUtils.setField(policy, "grade", grade);
		ReflectionTestUtils.setField(policy, "deductionType", type);
		ReflectionTestUtils.setField(policy, "fixedAmount", fixedAmount);
		ReflectionTestUtils.setField(policy, "capRate", capRate);
		return policy;
	}
}
