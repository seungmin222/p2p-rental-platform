package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.item.dto.AvailablePeriod;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.rental.dto.RentalExtensionConfirmed;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalOccupancyReader.OccupiedPeriod;

/**
 * 연장 요청의 트랜잭션 부분 (REQ-038). {@link RentalExtensionService}가 예약과 같은 물품 락({@code lock:item:{itemId}}) 안에서 호출한다.
 * 물품 락 → 대여 행 락 순서로 잡는다. 대여 행 락은 연체 배치·반납 요청·결제 승인과 한 줄로 서기 위해서다.
 */
@Service
public class RentalExtensionTxService {

	static final String MAX_END_DATE_FIELD = "maxEndDate";

	private final RentalRepository rentalRepository;
	private final RentalExtensionRepository rentalExtensionRepository;
	private final RentalOccupancyReader rentalOccupancyReader;
	private final RentalExtensionRecorder rentalExtensionRecorder;
	private final ItemQueryService itemQueryService;
	private final PolicyProperties policyProperties;
	private final boolean paymentEnabled;

	public RentalExtensionTxService(RentalRepository rentalRepository,
		RentalExtensionRepository rentalExtensionRepository, RentalOccupancyReader rentalOccupancyReader,
		RentalExtensionRecorder rentalExtensionRecorder, ItemQueryService itemQueryService,
		PolicyProperties policyProperties,
		@Value("${app.feature.payment-enabled}")
		boolean paymentEnabled) {
		this.rentalRepository = rentalRepository;
		this.rentalExtensionRepository = rentalExtensionRepository;
		this.rentalOccupancyReader = rentalOccupancyReader;
		this.rentalExtensionRecorder = rentalExtensionRecorder;
		this.itemQueryService = itemQueryService;
		this.policyProperties = policyProperties;
		this.paymentEnabled = paymentEnabled;
	}

	/**
	 * @param confirmed 결제 없이 바로 확정했으면 알림 받을 당사자. 결제 대기면 null
	 */
	public record Result(RentalExtension extension, RentalExtensionConfirmed confirmed) {
	}

	/**
	 * 검증 순서: 당사자·대여자 → 상태(OVERDUE 409, 그 외 RENTING 아님 409) → 횟수 → 일수 → 결제 대기 연장 1건 → 가능 기간 → 겹침.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_RENTER_ONLY, RENTAL_EXTENSION_OVERDUE,
	 *     RENTAL_INVALID_TRANSITION, RENTAL_EXTENSION_LIMIT_EXCEEDED, RENTAL_INVALID_PERIOD, RENTAL_EXTENSION_IN_PROGRESS,
	 *     RENTAL_OUT_OF_AVAILABLE_PERIOD(details maxEndDate), RENTAL_EXTENSION_PERIOD_CONFLICT(details maxEndDate)
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Result request(Long memberId, Long rentalId, LocalDate newEndDate, LocalDateTime now) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		RentalExtensionService.validateRenter(rental, memberId);
		PolicyProperties.Extension policy = policyProperties.extension();
		rental.validateExtendable(policy.maxCount(), now.toLocalDate());
		LocalDate prevEndDate = rental.getEndDate();
		validateDays(prevEndDate, newEndDate, policy.maxDaysPer());
		if (rentalExtensionRepository.existsPending(rentalId, now)) {
			throw new BusinessException(ErrorCode.RENTAL_EXTENSION_IN_PROGRESS);
		}
		validatePeriod(rental, newEndDate, policy.maxDaysPer(), now);

		int days = Rental.countDays(prevEndDate.plusDays(1), newEndDate);
		int additionalFee = Math.multiplyExact(rental.getPricePerDay(), days);
		if (paymentEnabled && additionalFee > 0) {
			LocalDateTime expiresAt = now.plusMinutes(policyProperties.payment().expireMinutes());
			RentalExtension extension = rentalExtensionRepository.save(
				RentalExtension.pendingPayment(rentalId, prevEndDate, newEndDate, additionalFee, expiresAt));
			return new Result(extension, null);
		}
		// 연장료 0원(무료 대여)·결제 기능 꺼짐: 결제 행 없이 바로 확정(payments 원칙 7, payment_id NULL)
		RentalExtension extension = rentalExtensionRepository.save(
			RentalExtension.confirmedWithoutPayment(rentalId, prevEndDate, newEndDate, additionalFee, now));
		rentalExtensionRecorder.apply(rental, extension, memberId, now);
		return new Result(extension,
			new RentalExtensionConfirmed(rentalId, extension.getId(), rental.getRenterId(), rental.getOwnerId()));
	}

	// newEnd > 기존 end (아니면 400 RENTAL_INVALID_PERIOD), 연장 일수 ≤ extension.max-days-per (아니면 400 LIMIT_EXCEEDED)
	private void validateDays(LocalDate prevEndDate, LocalDate newEndDate, int maxDaysPer) {
		if (!newEndDate.isAfter(prevEndDate)) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_PERIOD);
		}
		if (newEndDate.isAfter(prevEndDate.plusDays(maxDaysPer))) {
			throw new BusinessException(ErrorCode.RENTAL_EXTENSION_LIMIT_EXCEEDED);
		}
	}

	// 연장 구간이 가능 기간 하나 안에 있고, 다른 점유(이 대여 자신 제외)와 겹치지 않는다. 실패하면 연장 가능한 최대 날짜를 details에 싣는다
	private void validatePeriod(Rental rental, LocalDate newEndDate, int maxDaysPer, LocalDateTime now) {
		LocalDate prevEndDate = rental.getEndDate();
		LocalDate firstDate = prevEndDate.plusDays(1);
		List<AvailablePeriod> periods = itemQueryService.getAvailablePeriods(rental.getItemId());
		List<OccupiedPeriod> occupied = rentalOccupancyReader.findOccupied(rental.getItemId(), firstDate,
			prevEndDate.plusDays(maxDaysPer), now, rental.getId());
		Optional<LocalDate> maxEndDate = ExtensionPeriodCalculator.maxEndDate(prevEndDate, maxDaysPer, periods,
			occupied);
		if (periods.stream().noneMatch(period -> period.isCovering(firstDate, newEndDate))) {
			throw new BusinessException(ErrorCode.RENTAL_OUT_OF_AVAILABLE_PERIOD, details(maxEndDate));
		}
		boolean overlaps = occupied.stream()
			.anyMatch(period -> !period.startDate().isAfter(newEndDate) && !period.endDate().isBefore(firstDate));
		if (overlaps) {
			throw new BusinessException(ErrorCode.RENTAL_EXTENSION_PERIOD_CONFLICT, details(maxEndDate));
		}
	}

	private List<FieldErrorDetail> details(Optional<LocalDate> maxEndDate) {
		return maxEndDate.map(date -> List.of(new FieldErrorDetail(MAX_END_DATE_FIELD, date.toString())))
			.orElse(List.of());
	}
}
