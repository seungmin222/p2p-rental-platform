package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/**
 * 물품의 기간 점유를 모으는 유일한 곳. 예약 겹침 검사(REQ-013)와 캘린더 BOOKED(REQ-012)가 같이 쓴다.
 * 트랜잭션은 호출하는 쪽을 따른다.
 */
@Component
@RequiredArgsConstructor
public class RentalOccupancyReader {

	private final RentalRepository rentalRepository;

	/** [from, to]와 겹치는 점유 기간 (양 끝 포함, 시작일 순) */
	public List<OccupiedPeriod> findOccupied(Long itemId, LocalDate from, LocalDate to, LocalDateTime now) {
		// REQ-038: 결제 대기 중인 연장 기간(rental_extensions PENDING_PAYMENT, 만료 전)을 여기에 합친다
		return rentalRepository.findOccupying(itemId, RentalStatus.ACTIVE_FOR_PERIOD, from, to, now).stream()
			.map(rental -> new OccupiedPeriod(rental.getStartDate(), rental.getEndDate()))
			.toList();
	}

	public boolean isOccupied(Long itemId, LocalDate from, LocalDate to, LocalDateTime now) {
		return !findOccupied(itemId, from, to, now).isEmpty();
	}

	/** 점유 기간 [startDate, endDate] (양 끝 포함) */
	public record OccupiedPeriod(LocalDate startDate, LocalDate endDate) {

		public boolean contains(LocalDate date) {
			return !date.isBefore(startDate) && !date.isAfter(endDate);
		}
	}
}
