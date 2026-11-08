package com.team7.rental.rental.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalExtension;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * RENTING → RENTING 연장 반영의 유일한 입구 (REQ-038): 종료일 갱신, 연장 횟수 + 1, 이력. 결제 없는 확정과 결제 승인이 같이 쓴다.
 * 호출하는 쪽의 트랜잭션에 참여하고, 대여 행 락을 이미 잡았다.
 */
@Component
@RequiredArgsConstructor
public class RentalExtensionRecorder {

	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final PolicyProperties policyProperties;

	/**
	 * @param changedBy 결제 없이 확정하면 대여자, 결제 승인이면 null(시스템)
	 */
	public void apply(Rental rental, RentalExtension extension, Long changedBy, LocalDateTime now) {
		RentalStatus from = rental.getStatus();
		rental.extend(extension.getPrevEndDate(), extension.getNewEndDate(), policyProperties.extension().maxCount());
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rental.getId(), from, rental.getStatus(),
			changedBy, RentalStatusHistory.REASON_EXTENSION, now));
	}
}
