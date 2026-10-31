package com.team7.rental.rental.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.rental.dto.CancelAgreementRespondRequest;
import com.team7.rental.rental.dto.CancelAgreementResponse;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;

import lombok.RequiredArgsConstructor;

/**
 * 예약 취소 (REQ-015). 트랜잭션을 열지 않고 [TX1] 취소·환불 기록({@link RentalCancelTxService}) → PG 환불 순서로 잇는다.
 * 환불이 실패해도 대여는 CANCELED이고 기간 선점도 풀린 채로, 재시도 배치가 환불을 끝낸다(docs/domains/rental.md REQ-015).
 */
@Service
@RequiredArgsConstructor
public class RentalCancelService {

	private final RentalCancelTxService rentalCancelTxService;
	private final RentalRefundPort rentalRefundPort;
	private final RentalQueryService rentalQueryService;

	/**
	 * 단독 취소. 취소 종류는 요청자의 역할(RENTER·OWNER)로 정한다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_CANCEL_NOT_ALLOWED
	 */
	public RentalDetailResponse cancel(Long rentalId, Long memberId, RentalCancelRequest request) {
		rentalCancelTxService.cancel(rentalId, memberId, request).ifPresent(rentalRefundPort::execute);
		return rentalQueryService.getRental(rentalId, memberId);
	}

	/**
	 * 양측 동의 취소 요청. RESERVED이고 오늘 ≥ 시작일일 때 당사자 한쪽이 보낸다. 거절·만료된 뒤에는 다시 보낼 수 있다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION(RESERVED 아님),
	 *     RENTAL_CANCEL_AGREEMENT_TOO_EARLY(시작일 전, 단독 취소 대상), RENTAL_CANCEL_AGREEMENT_IN_PROGRESS
	 */
	public CancelAgreementResponse requestAgreement(Long rentalId, Long memberId, RentalCancelRequest request) {
		return rentalCancelTxService.requestAgreement(rentalId, memberId, request);
	}

	/**
	 * 양측 동의 취소 응답. 요청하지 않은 쪽만 응답한다. ACCEPT → 대여 CANCELED(MUTUAL, 요청자의 사유) + 전액 환불, REJECT → 요청만 종료.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_CANCEL_AGREEMENT_NOT_FOUND(다른 대여의 요청
	 *     포함), RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE, RENTAL_CANCEL_AGREEMENT_CLOSED(이미 응답·만료),
	 *     RENTAL_INVALID_TRANSITION(수락 시 대여가 RESERVED가 아님)
	 */
	public RentalDetailResponse respondAgreement(Long rentalId, Long agreementId, Long memberId,
		CancelAgreementRespondRequest request) {
		rentalCancelTxService.respondAgreement(rentalId, agreementId, memberId, request)
			.ifPresent(rentalRefundPort::execute);
		return rentalQueryService.getRental(rentalId, memberId);
	}

	/**
	 * 만료 배치(CancelAgreementExpireJob)용.
	 *
	 * @return 만료한 건수
	 */
	public int expireOverdue(LocalDateTime now) {
		return rentalCancelTxService.expireOverdue(now);
	}
}
