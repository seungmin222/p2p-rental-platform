package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.rental.dto.CalendarDayStatus;
import com.team7.rental.rental.dto.RentalCalendarResponse;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.dto.RentalInspectionResponse;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalQueryService {

	private final RentalRepository rentalRepository;
	private final RentalCancelAgreementRepository rentalCancelAgreementRepository;
	private final ItemInspectionRepository itemInspectionRepository;
	private final RentalOccupancyReader rentalOccupancyReader;
	private final ItemQueryService itemQueryService;
	private final MemberQueryService memberQueryService;
	private final Clock clock;

	/**
	 * 대여 상세(REQ-016, SCR-08). 당사자만 본다. 취소 쓰기(REQ-015)도 같은 트랜잭션 안에서 이 메서드로 응답을 만든다.
	 * 진행 중인 동의 취소 조회가 변경 전 행을 보지 않는 것은 JPQL 실행 전 자동 flush 덕분이다.
	 * <p>
	 * 동의 취소 요청은 대여를 수정하지 않아서 인계 뒤에도 REQUESTED 행이 남을 수 있다(만료 배치가 정리). 그래서 activeCancelAgreement는 대여가
	 * RESERVED일 때만 채운다. 조회는 상태와 상관없이 해 쿼리 수를 일정하게 둔다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT
	 */
	public RentalDetailResponse getRental(Long rentalId, Long memberId) {
		Rental rental = rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		ItemSummary item = itemQueryService.getSummary(rental.getItemId());
		Long counterpartId = rental.counterpartOf(memberId);
		RentalCancelAgreement activeCancelAgreement = rentalCancelAgreementRepository
			.findOpen(rentalId, LocalDateTime.now(clock))
			.filter(agreement -> rental.getStatus() == RentalStatus.RESERVED)
			.orElse(null);
		return RentalDetailResponse.of(rental, rental.roleOf(memberId), item, counterpartId,
			memberQueryService.getNickname(counterpartId), activeCancelAgreement, memberId);
	}

	/**
	 * 인계·반납 기록(REQ-017·018). 당사자만 본다. HANDOVER → RETURN 순(인계가 반납보다 먼저 저장된다).
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT
	 */
	public List<RentalInspectionResponse> getInspections(Long rentalId, Long memberId) {
		Rental rental = rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		return itemInspectionRepository.findAllByRentalIdOrderById(rentalId).stream()
			.map(inspection -> RentalInspectionResponse.of(inspection, rental.roleOf(inspection.getWrittenBy())))
			.toList();
	}

	/**
	 * 대여 캘린더(REQ-012). 그 달과 겹치는 점유를 한 번에 읽어 날짜에 매핑한다.
	 * 지난 날짜·가능 기간 밖 → UNAVAILABLE, 점유 → BOOKED, 나머지 → AVAILABLE (오늘은 지난 날짜가 아니다).
	 *
	 * @param month null이면 이번 달
	 * @param memberId 비회원이면 null. HIDDEN 물품은 소유자만 본다
	 * @throws BusinessException ITEM_NOT_FOUND
	 */
	public RentalCalendarResponse getCalendar(Long itemId, YearMonth month, Long memberId) {
		itemQueryService.validateVisible(itemId, memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		YearMonth targetMonth = month != null ? month : YearMonth.from(today);
		LocalDate from = targetMonth.atDay(1);
		LocalDate to = targetMonth.atEndOfMonth();

		List<AvailablePeriod> availablePeriods = itemQueryService.getAvailablePeriods(itemId);
		List<OccupiedPeriod> occupiedPeriods = rentalOccupancyReader.findOccupied(itemId, from, to, now);

		List<RentalCalendarResponse.CalendarDay> days = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			days.add(
				new RentalCalendarResponse.CalendarDay(date, statusOf(date, today, availablePeriods, occupiedPeriods)));
		}
		return new RentalCalendarResponse(targetMonth, days);
	}

	private CalendarDayStatus statusOf(LocalDate date, LocalDate today, List<AvailablePeriod> availablePeriods,
		List<OccupiedPeriod> occupiedPeriods) {
		if (date.isBefore(today) || availablePeriods.stream().noneMatch(period -> period.contains(date))) {
			return CalendarDayStatus.UNAVAILABLE;
		}
		if (occupiedPeriods.stream().anyMatch(period -> period.contains(date))) {
			return CalendarDayStatus.BOOKED;
		}
		return CalendarDayStatus.AVAILABLE;
	}
}
