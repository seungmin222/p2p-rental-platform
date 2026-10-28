package com.team7.rental.rental.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/**
 * 취소 수수료 (REQ-015, policies.md §2 취소 수수료 표). 1순위는 rentals.cancel_fee에 기록만 하고 환불·정산은 2순위.
 * 호출 전에 {@link Rental#validateCancelable}을 통과해야 한다.
 */
@Component
@RequiredArgsConstructor
public class CancelFeeCalculator {

	private final PolicyProperties policyProperties;

	/**
	 * 결제 대기 0, 제공자 0, 양측 동의 0. 대여자는 오늘 ≤ 시작일 − cancel-fee.free-until-days-before면 0, 그 뒤 시작일 전까지
	 * 대여료 × cancel-fee.rate-percent / 100 (원 미만 절사).
	 *
	 * @throws IllegalArgumentException RESERVED 단독 취소인데 오늘 ≥ 시작일(validateCancelable에서 막히는 경우), 지원하지 않는 취소 종류
	 */
	public int calculate(Rental rental, CancelType type, LocalDate today) {
		// 결제 대기는 시작일이 지났어도 0이다(자정 직전 생성). 날짜 검사보다 먼저 본다
		if (rental.getStatus() == RentalStatus.PENDING_PAYMENT) {
			return 0;
		}
		return switch (type) {
			case MUTUAL -> 0;
			case OWNER -> {
				requireBeforeStart(rental, today);
				yield 0;
			}
			case RENTER -> {
				requireBeforeStart(rental, today);
				yield renterFee(rental, today);
			}
			default -> throw new IllegalArgumentException("지원하지 않는 취소 종류입니다: " + type);
		};
	}

	private int renterFee(Rental rental, LocalDate today) {
		PolicyProperties.CancelFee policy = policyProperties.cancelFee();
		LocalDate freeUntil = rental.getStartDate().minusDays(policy.freeUntilDaysBefore());
		if (!today.isAfter(freeUntil)) {
			return 0;
		}
		return Math.toIntExact((long)rental.getRentalFee() * policy.ratePercent() / 100);
	}

	private void requireBeforeStart(Rental rental, LocalDate today) {
		if (!today.isBefore(rental.getStartDate())) {
			throw new IllegalArgumentException("시작일 이후 단독 취소는 수수료 계산 대상이 아닙니다: " + rental.getId());
		}
	}
}
