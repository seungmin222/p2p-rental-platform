package com.team7.rental.rental.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/** 다른 도메인용 대여 건수 조회. 사용자 프로필(REQ-021)의 거래 횟수에 쓴다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalCountReader {

	private final RentalRepository rentalRepository;

	/**
	 * 회원이 대여자 또는 제공자인 거래 완료(COMPLETED) 대여 수. 대여자·제공자 인덱스를 각각 타도록 OR 대신 쿼리 2개로 센다. 대여자와 제공자는 같은 회원일 수
	 * 없어 두 번 세는 건이 없다.
	 */
	public long countCompleted(Long memberId) {
		return rentalRepository.countByRenterIdAndStatus(memberId, RentalStatus.COMPLETED)
			+ rentalRepository.countByOwnerIdAndStatus(memberId, RentalStatus.COMPLETED);
	}
}
