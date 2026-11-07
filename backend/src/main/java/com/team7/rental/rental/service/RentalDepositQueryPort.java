package com.team7.rental.rental.service;

import java.util.Optional;

import com.team7.rental.rental.dto.RentalDepositInfo;

/** 대여 상세가 읽는 보증금 정보 (REQ-025·049). 구현은 결제 도메인에 있어 rental이 payment를 직접 알지 않는다({@link RentalRefundPort}와 같은 이유) */
public interface RentalDepositQueryPort {

	/** @return 승인된 대여 결제(type=RENTAL)가 없으면(결제 없음·결제 대기·무료 대여) 비어 있다 */
	Optional<RentalDepositInfo> findDepositInfo(Long rentalId);
}
