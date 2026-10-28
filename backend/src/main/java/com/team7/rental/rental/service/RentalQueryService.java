package com.team7.rental.rental.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalQueryService {

	private final RentalRepository rentalRepository;
	private final ItemQueryService itemQueryService;
	private final MemberQueryService memberQueryService;

	/**
	 * 대여 상세(REQ-016, SCR-08). 당사자만 본다.
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
		return RentalDetailResponse.of(rental, rental.roleOf(memberId), item, counterpartId,
			memberQueryService.getNickname(counterpartId));
	}
}
