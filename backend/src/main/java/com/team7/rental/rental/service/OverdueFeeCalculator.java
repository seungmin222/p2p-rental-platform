package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/**
 * 연체료 (REQ-023, policies.md §4 계산 규칙). 상태가 아니라 날짜로 계산한다: 연체 배치가 OVERDUE로 바꾸기 전(00:00~00:05)에 반납해도 금액이 같다.
 * <p>
 * 기준일을 인자로 받는다. 반납 요청은 반납 요청일, 대여 상세는 오늘, 미반납 신고(REQ-049, open-issues P4)는 신고일을 넘긴다.
 */
@Component
@RequiredArgsConstructor
public class OverdueFeeCalculator {

	private final PolicyProperties policyProperties;

	/** 연체 일수 = baseDate − endDate. 반납 예정일 당일까지는 0이다 */
	public static int overdueDays(LocalDate endDate, LocalDate baseDate) {
		return Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(endDate, baseDate)));
	}

	/** {@link #calculate(int, int, LocalDate, LocalDate)}에 대여의 예약 시점 스냅샷(일 대여료·보증금)과 종료일을 넘긴다 */
	public int calculate(Rental rental, LocalDate baseDate) {
		return calculate(rental.getPricePerDay(), rental.getDeposit(), rental.getEndDate(), baseDate);
	}

	/**
	 * 대여 상세에 보여 줄 연체료. 아직 물건이 대여자에게 있고(OVERDUE, 또는 배치 전이라 RENTING) 반납 예정일이 지났으면 오늘 기준 현재까지 연체료를,
	 * 그 외 상태는 반납 요청 때 확정한 rentals.overdue_fee(반납 전이면 0)를 돌려준다.
	 */
	public int displayFee(Rental rental, LocalDate today) {
		RentalStatus status = rental.getStatus();
		if (status == RentalStatus.OVERDUE || status == RentalStatus.RENTING) {
			return calculate(rental, today);
		}
		return rental.getOverdueFee();
	}

	/**
	 * 연체료 = min(연체 일수 × floor(일 대여료 × overdue-fee.rate / 100), 보증금).
	 * 보증금 0원이면 0이다(policies.md "무료 대여": 연체료 징수를 건너뛰고 신뢰 점수에만 반영. 연체 일수는 returnedAt − endDate로 다시 구한다).
	 */
	public int calculate(int pricePerDay, int deposit, LocalDate endDate, LocalDate baseDate) {
		int days = overdueDays(endDate, baseDate);
		if (days == 0 || deposit == 0) {
			return 0;
		}
		long perDay = (long)pricePerDay * policyProperties.overdueFee().ratePercent() / 100;
		return Math.toIntExact(Math.min(perDay * days, deposit));
	}
}
