package com.team7.rental.rental.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.rental.dto.RentalParticipants;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/** 다른 도메인용 대여 당사자 조회 (문의·분쟁 REQ-063). 다른 도메인이 RentalRepository를 직접 부르지 않게 한다 */
@Service
@RequiredArgsConstructor
public class RentalParticipantReader {

	private final RentalRepository rentalRepository;

	/** @throws BusinessException RENTAL_NOT_FOUND */
	@Transactional(readOnly = true)
	public RentalParticipants get(Long rentalId) {
		return toParticipants(rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND)));
	}

	/**
	 * 대여 행 락을 잡고 읽는다. 분쟁 접수가 대여 완료(정산 생성)와 한 줄로 서게 해, 접수 직후 만들어지는 정산도 보류된다.
	 * 락은 호출하는 쪽 트랜잭션이 끝날 때 풀린다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RentalParticipants lock(Long rentalId) {
		return toParticipants(rentalRepository.findByIdForUpdate(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND)));
	}

	private RentalParticipants toParticipants(Rental rental) {
		return new RentalParticipants(rental.getId(), rental.getRenterId(), rental.getOwnerId(), rental.getStatus());
	}
}
