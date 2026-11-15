package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/**
 * 물품의 기간 점유를 모으는 유일한 곳. 예약 겹침 검사(REQ-013), 캘린더 BOOKED(REQ-012), 연장 겹침 검사(REQ-038)가 같이 쓴다.
 * 점유 = ACTIVE_FOR_PERIOD 대여(결제 대기는 만료 전만) + 만료 전 결제 대기 연장의 연장 기간. 대여의 점유 끝은
 * {@link Rental#occupiedEndDate}(RENTING·OVERDUE는 반납 전까지 오늘까지 늘어남, open-issues #53). 트랜잭션은 호출하는 쪽을 따른다.
 * 물품 검색(REQ-010, ItemListingRepositoryImpl.occupied)이 같은 조건을 쿼리로 다시 쓰므로, 점유 조건을 바꾸면 그쪽도 함께 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class RentalOccupancyReader {

	private final RentalRepository rentalRepository;
	private final RentalExtensionRepository rentalExtensionRepository;

	/** [from, to]와 겹치는 점유 기간 (양 끝 포함, 시작일 순) */
	public List<OccupiedPeriod> findOccupied(Long itemId, LocalDate from, LocalDate to, LocalDateTime now) {
		return findOccupied(itemId, from, to, now, null);
	}

	/**
	 * @param excludeRentalId 이 대여 자체의 기간은 빼고 본다(연장 검사). 그 대여의 결제 대기 연장은 빼지 않는다(대여당 1건 제한이 먼저 막는다)
	 */
	public List<OccupiedPeriod> findOccupied(Long itemId, LocalDate from, LocalDate to, LocalDateTime now,
		Long excludeRentalId) {
		LocalDate today = now.toLocalDate();
		return collect(itemId, from, to, now, excludeRentalId,
			rental -> new OccupiedPeriod(rental.getStartDate(), rental.occupiedEndDate(today)));
	}

	/**
	 * 대여 가능 기간 수정 검사(REQ-007)용. 점유하는 대여는 {@link #findOccupied}와 같지만, 대여 기간은 연체로 늘어난 구간 없이
	 * 원래 대여 날짜 [startDate, endDate]로 돌려준다. 늘어난 구간은 원래 가능 기간 밖일 수 있어서, 넣으면 연체 한 건으로 물품 수정이
	 * 모두 막히기 때문이다(open-issues #53). 결제 대기 연장 기간은 그대로 담는다. 쿼리는 늘어난 구간으로 고르므로, 원래 날짜가
	 * [from, to]와 겹치는 것만 한 번 더 거른다(좁은 범위로 불려도 범위 밖 연체 건이 섞이지 않게)
	 */
	public List<OccupiedPeriod> findBookedDates(Long itemId, LocalDate from, LocalDate to, LocalDateTime now) {
		return collect(itemId, from, to, now, null,
			rental -> new OccupiedPeriod(rental.getStartDate(), rental.getEndDate())).stream()
			.filter(period -> period.overlaps(from, to))
			.toList();
	}

	private List<OccupiedPeriod> collect(Long itemId, LocalDate from, LocalDate to, LocalDateTime now,
		Long excludeRentalId, Function<Rental, OccupiedPeriod> rentalPeriod) {
		Stream<OccupiedPeriod> rentals = rentalRepository
			.findOccupying(itemId, RentalStatus.ACTIVE_FOR_PERIOD, from, to, now.toLocalDate(), now).stream()
			.filter(rental -> !rental.getId().equals(excludeRentalId))
			.map(rentalPeriod);
		Stream<OccupiedPeriod> extensions = rentalExtensionRepository.findPendingOccupying(itemId, from, to, now)
			.stream()
			.map(extension -> new OccupiedPeriod(extension.firstDate(), extension.getNewEndDate()));
		return Stream.concat(rentals, extensions)
			.sorted(Comparator.comparing(OccupiedPeriod::startDate))
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

		/** [from, to]와 하루라도 겹치면 true (양 끝 포함) */
		public boolean overlaps(LocalDate from, LocalDate to) {
			return !startDate.isAfter(to) && !endDate.isBefore(from);
		}
	}
}
