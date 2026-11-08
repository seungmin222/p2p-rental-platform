package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;

/**
 * 연장 가능한 최대 종료일 (REQ-038 "409 + 연장 가능한 최대 날짜"). 상태가 없는 계산이라 단위 테스트로 경계를 본다.
 */
public final class ExtensionPeriodCalculator {

	private ExtensionPeriodCalculator() {}

	/**
	 * 다음 중 가장 이른 날: 기존 종료일 + maxDaysPer, 기존 종료일 다음 날이 들어 있는 가능 기간의 끝, 연장 구간에서 가장 먼저 시작하는 점유의 전날.
	 *
	 * @param occupied 연장 구간 [prevEndDate + 1, prevEndDate + maxDaysPer]과 겹치는 점유(이 대여 자신은 뺀 것)
	 * @return 하루도 연장할 수 없으면 비어 있다
	 */
	public static Optional<LocalDate> maxEndDate(LocalDate prevEndDate, int maxDaysPer,
		List<AvailablePeriod> availablePeriods, List<OccupiedPeriod> occupied) {
		LocalDate firstDate = prevEndDate.plusDays(1);
		Optional<AvailablePeriod> covering = availablePeriods.stream()
			.filter(period -> period.isCovering(firstDate, firstDate))
			.findFirst();
		if (covering.isEmpty()) {
			return Optional.empty();
		}
		LocalDate max = min(prevEndDate.plusDays(maxDaysPer), covering.get().endDate());
		for (OccupiedPeriod period : occupied) {
			if (!period.endDate().isBefore(firstDate)) {
				max = min(max, period.startDate().minusDays(1));
			}
		}
		return max.isBefore(firstDate) ? Optional.empty() : Optional.of(max);
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}
}
