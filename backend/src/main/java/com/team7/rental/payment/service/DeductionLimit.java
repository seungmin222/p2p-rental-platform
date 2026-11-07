package com.team7.rental.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DamagePolicy;
import com.team7.rental.payment.entity.DeductionType;

/**
 * 등급별 차감 상한 (REQ-049, policies.md §4 계산 규칙). 상한 조회·제안·관리자 확정이 같은 값으로 검사한다.
 * <pre>
 * 남은 보증금 = 보증금 − 연체료
 * 상한: FIXED(B) = 정액, ACTUAL이고 capRate 없음(TOOL의 B 실비) = 남은 보증금, ACTUAL이고 capRate 있음(C) = floor(보증금 × capRate),
 *       FULL(D) = 남은 보증금
 * 제안 금액 조건: 0 ≤ 금액 ≤ min(상한, 남은 보증금)
 * </pre>
 *
 * @param cap 등급별 상한 (남은 보증금으로 자르기 전)
 * @param maxAmount 제안할 수 있는 최대 금액 = min(cap, remainingDeposit)
 * @param receiptRequired 견적서·영수증·구매 내역이 필요한지 (deduction_type ACTUAL: C 전부, TOOL의 B)
 */
public record DeductionLimit(DamageGrade grade, DeductionType deductionType, int deposit, int overdueFee,
	int remainingDeposit, int cap, int maxAmount, boolean receiptRequired) {

	/**
	 * @param lostReported 미반납 신고 건이면 D 등급만
	 * @throws BusinessException DEDUCTION_GRADE_NOT_ALLOWED: A 등급(차감 불가)이거나 미반납 신고 건에서 D가 아닐 때
	 */
	public static DeductionLimit of(DamagePolicy policy, int deposit, int overdueFee, boolean lostReported) {
		DamageGrade grade = policy.getGrade();
		if (policy.getDeductionType() == DeductionType.NONE || (lostReported && grade != DamageGrade.D)) {
			throw new BusinessException(ErrorCode.DEDUCTION_GRADE_NOT_ALLOWED);
		}
		int remaining = Math.max(0, deposit - overdueFee);
		int cap = switch (policy.getDeductionType()) {
			case FIXED -> policy.getFixedAmount();
			case ACTUAL -> policy.getCapRate() == null ? remaining
				: BigDecimal.valueOf(deposit).multiply(policy.getCapRate()).setScale(0, RoundingMode.DOWN)
					.intValueExact();
			case FULL -> remaining;
			case NONE -> throw new IllegalStateException("unreachable");
		};
		return new DeductionLimit(grade, policy.getDeductionType(), deposit, overdueFee, remaining, cap,
			Math.min(cap, remaining), policy.getDeductionType() == DeductionType.ACTUAL);
	}

	/**
	 * @throws BusinessException DEDUCTION_EXCEEDS_DEPOSIT(남은 보증금 초과), DEDUCTION_EXCEEDS_CAP(상한 초과, details에 maxAmount)
	 * @throws IllegalArgumentException 음수 (요청 검증이 먼저 막는다)
	 */
	public void validate(int amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("차감액은 0원 이상이어야 합니다: " + amount);
		}
		if (amount > remainingDeposit) {
			throw new BusinessException(ErrorCode.DEDUCTION_EXCEEDS_DEPOSIT);
		}
		if (amount > cap) {
			throw new BusinessException(ErrorCode.DEDUCTION_EXCEEDS_CAP,
				List.of(new FieldErrorDetail("maxAmount", String.valueOf(maxAmount))));
		}
	}

	/** 확정 시 반환액 = 보증금 − 연체료 − 차감액 */
	public int refundAmount(int amount) {
		return remainingDeposit - amount;
	}
}
