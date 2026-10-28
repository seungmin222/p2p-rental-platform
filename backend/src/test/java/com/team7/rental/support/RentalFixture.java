package com.team7.rental.support;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;

/**
 * 지정한 상태·기간의 대여를 바로 저장(커밋)한다. 피드 "대여 중" 배지(REQ-009)·기간 검색(REQ-010) 같은 조회 테스트용.
 * 상태만 바꾸고 handedOverAt 같은 시각 컬럼은 채우지 않는다.
 * 예: {@code rentalFixture.createRental(item, renter, RentalStatus.RENTING, start, end);}
 */
@TestComponent
public class RentalFixture {

	private final RentalRepository rentalRepository;

	RentalFixture(RentalRepository rentalRepository) {
		this.rentalRepository = rentalRepository;
	}

	/**
	 * item의 가격·보증금을 스냅샷한 대여. PENDING_PAYMENT는 만료 시각이 필요하므로 {@link #createPendingPayment}를 쓴다.
	 */
	public Rental createRental(Item item, Member renter, RentalStatus status, LocalDate start, LocalDate end) {
		if (status == RentalStatus.PENDING_PAYMENT) {
			throw new IllegalArgumentException("PENDING_PAYMENT는 createPendingPayment를 쓴다");
		}
		Rental rental = Rental.createReserved(item.getId(), item.getOwnerId(), renter.getId(), start, end,
			item.getPricePerDay(), item.getDeposit());
		// 상태 전이 메서드(REQ-015~018)가 아직 없다. 생기면 그 메서드로 바꾼다
		ReflectionTestUtils.setField(rental, "status", status);
		return rentalRepository.save(rental);
	}

	public Rental createPendingPayment(Item item, Member renter, LocalDate start, LocalDate end,
		LocalDateTime paymentExpiresAt) {
		return rentalRepository.save(Rental.createPendingPayment(item.getId(), item.getOwnerId(), renter.getId(),
			start, end, item.getPricePerDay(), item.getDeposit(), paymentExpiresAt));
	}
}
