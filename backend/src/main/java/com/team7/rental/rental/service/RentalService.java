package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.member.service.MemberRegionQueryService;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.Rental;

import lombok.RequiredArgsConstructor;

/**
 * 대여 쓰기. 클래스에 트랜잭션을 걸지 않는다 — 락 획득 → 트랜잭션(별도 빈) → 커밋 → 락 해제 순서를 지키기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class RentalService {

	private final RentalLockStrategy rentalLockStrategy;
	private final RentalReservationTxService rentalReservationTxService;
	private final RentalQueryService rentalQueryService;
	private final ItemQueryService itemQueryService;
	private final MemberRegionQueryService memberRegionQueryService;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 예약 요청(REQ-013·014). 검증 순서: 기간 → 물품 → 본인 물품 → 대표 동네 인증 → 가능 기간 → (락 안에서) 겹침.
	 * 락 밖 검증은 락 점유 시간을 줄이려는 것이고, 최종 판정(겹침)은 락 안 트랜잭션에서 한다.
	 */
	public RentalDetailResponse create(Long renterId, RentalCreateRequest request) {
		// 현재 시각은 여기서 한 번만 읽는다. 락 대기(최대 rental.lock-wait)만큼 과거가 될 수 있지만 결제 만료 시각이 그만큼 당겨질 뿐이라
		// payment.expire-minutes 단위의 만료에는 영향이 없다
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate startDate = request.startDate();
		LocalDate endDate = request.endDate();
		validatePeriod(startDate, endDate, now.toLocalDate());
		ItemRentalTarget item = itemQueryService.getRentalTarget(request.itemId(), renterId);
		if (item.isOwnedBy(renterId)) {
			throw new BusinessException(ErrorCode.RENTAL_OWN_ITEM);
		}
		if (!memberRegionQueryService.isPrimaryRegionVerified(renterId)) {
			throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
		}
		validateWithinAvailablePeriod(item.itemId(), startDate, endDate);

		Rental rental = rentalLockStrategy.executeWithItemLock(item.itemId(),
			() -> rentalReservationTxService.create(renterId, item, startDate, endDate, now));
		return rentalQueryService.getRental(rental.getId(), renterId);
	}

	// 오늘 이후 시작, 시작 ≤ 종료, 일수 rental.min-days ~ max-days
	private void validatePeriod(LocalDate startDate, LocalDate endDate, LocalDate today) {
		if (startDate.isBefore(today) || endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_PERIOD);
		}
		int days = Rental.countDays(startDate, endDate);
		if (days < policyProperties.rental().minDays() || days > policyProperties.rental().maxDays()) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_PERIOD);
		}
	}

	// 가능 기간 하나에 완전히 들어가야 한다. 이어진 두 기간에 걸쳐도 안 된다
	private void validateWithinAvailablePeriod(Long itemId, LocalDate startDate, LocalDate endDate) {
		boolean isCovered = itemQueryService.getAvailablePeriods(itemId).stream()
			.anyMatch(period -> period.isCovering(startDate, endDate));
		if (!isCovered) {
			throw new BusinessException(ErrorCode.RENTAL_OUT_OF_AVAILABLE_PERIOD);
		}
	}
}
