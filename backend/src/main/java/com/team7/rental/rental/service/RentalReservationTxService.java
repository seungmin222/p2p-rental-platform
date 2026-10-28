package com.team7.rental.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

/**
 * 예약의 트랜잭션 부분(REQ-013·014): 겹침 검사 → INSERT → 생성 이력.
 * RentalService가 물품 락 안에서 호출한다. 별도 빈이어야 트랜잭션 프록시가 걸리고 커밋이 락 해제보다 먼저 일어난다.
 */
@Service
public class RentalReservationTxService {

	private final RentalRepository rentalRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final RentalOccupancyReader rentalOccupancyReader;
	private final PolicyProperties policyProperties;
	private final boolean paymentEnabled;

	public RentalReservationTxService(RentalRepository rentalRepository,
		RentalStatusHistoryRepository rentalStatusHistoryRepository, RentalOccupancyReader rentalOccupancyReader,
		PolicyProperties policyProperties,
		@Value("${app.feature.payment-enabled}")
		boolean paymentEnabled) {
		this.rentalRepository = rentalRepository;
		this.rentalStatusHistoryRepository = rentalStatusHistoryRepository;
		this.rentalOccupancyReader = rentalOccupancyReader;
		this.policyProperties = policyProperties;
		this.paymentEnabled = paymentEnabled;
	}

	/**
	 * 입력 검증(기간·물품·동네·가능 기간)은 호출하는 쪽이 락 밖에서 끝낸다.
	 *
	 * @param now 호출하는 쪽이 검증에 쓴 현재 시각. 결제 대기 만료·이력 시각에 같이 쓴다
	 * @throws BusinessException RENTAL_PERIOD_CONFLICT: 점유 중인 기간과 겹칠 때
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Rental create(Long renterId, ItemRentalTarget item, LocalDate startDate, LocalDate endDate,
		LocalDateTime now) {
		if (rentalOccupancyReader.isOccupied(item.itemId(), startDate, endDate, now)) {
			throw new BusinessException(ErrorCode.RENTAL_PERIOD_CONFLICT);
		}
		Rental rental = rentalRepository.save(newRental(renterId, item, startDate, endDate, now));
		// 이력은 같은 트랜잭션에서 저장한다(커밋 후 리스너로 저장하면 실패 시 이력이 빠진다)
		rentalStatusHistoryRepository.save(
			RentalStatusHistory.create(rental.getId(), null, rental.getStatus(), renterId, null, now));
		return rental;
	}

	// 결제를 켰고 결제 금액이 있으면 결제 대기, 아니면(1순위 단계·무료 대여) 바로 확정 (state-machines.md §1)
	private Rental newRental(Long renterId, ItemRentalTarget item, LocalDate startDate, LocalDate endDate,
		LocalDateTime now) {
		long paymentAmount = (long)item.pricePerDay() * Rental.countDays(startDate, endDate) + item.deposit();
		if (paymentEnabled && paymentAmount > 0) {
			LocalDateTime expiresAt = now.plusMinutes(policyProperties.payment().expireMinutes());
			return Rental.createPendingPayment(item.itemId(), item.ownerId(), renterId, startDate, endDate,
				item.pricePerDay(), item.deposit(), expiresAt);
		}
		return Rental.createReserved(item.itemId(), item.ownerId(), renterId, startDate, endDate,
			item.pricePerDay(), item.deposit());
	}
}
