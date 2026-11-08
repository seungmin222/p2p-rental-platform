package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.rental.dto.RentalExtensionConfirmed;
import com.team7.rental.rental.dto.RentalExtensionRequest;
import com.team7.rental.rental.dto.RentalExtensionResponse;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalExtensionTxService.Result;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;

import lombok.RequiredArgsConstructor;

/**
 * 대여 연장 요청 (REQ-038). 클래스에 트랜잭션을 걸지 않는다. 물품 락 → 트랜잭션(별도 빈) → 커밋 → 락 해제 순서를 지키기 위해서다(RentalService와 같다).
 */
@Service
@RequiredArgsConstructor
public class RentalExtensionService {

	private final RentalRepository rentalRepository;
	private final RentalLockStrategy rentalLockStrategy;
	private final RentalExtensionTxService rentalExtensionTxService;
	private final RentalNotificationPort rentalNotificationPort;
	private final Clock clock;

	/**
	 * 락 밖에서는 물품 ID를 얻고 당사자만 확인한다(락 점유 시간을 줄이려고). 나머지 판정은 락 안 트랜잭션이 대여 행 락을 잡고 한다.
	 *
	 * @throws BusinessException {@link RentalExtensionTxService#request}와 같고, 그 밖에 RENTAL_LOCK_TIMEOUT
	 */
	public RentalExtensionResponse request(Long memberId, Long rentalId, RentalExtensionRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		validateRenter(rental, memberId);
		Result result = rentalLockStrategy.executeWithItemLock(rental.getItemId(),
			() -> rentalExtensionTxService.request(memberId, rentalId, request.newEndDate(), now));
		if (result.confirmed() != null) {
			notifyExtended(rentalNotificationPort, result.confirmed());
		}
		return RentalExtensionResponse.of(result.extension());
	}

	/** 연장 확정 알림 (양측, 커밋 뒤). 결제 승인으로 확정한 연장은 결제 쪽이 같은 메서드로 보낸다 */
	public static void notifyExtended(RentalNotificationPort port, RentalExtensionConfirmed confirmed) {
		String eventId = RentalNotificationPort.extensionEventId(confirmed.rentalId(), confirmed.extensionId());
		port.send(confirmed.renterId(), Type.RENTAL_EXTENDED, TargetType.RENTAL, confirmed.rentalId(), eventId);
		port.send(confirmed.ownerId(), Type.RENTAL_EXTENDED, TargetType.RENTAL, confirmed.rentalId(), eventId);
	}

	/**
	 * @throws BusinessException RENTAL_NOT_PARTICIPANT, RENTAL_RENTER_ONLY(제공자)
	 */
	static void validateRenter(Rental rental, Long memberId) {
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		if (!rental.getRenterId().equals(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_RENTER_ONLY);
		}
	}
}
