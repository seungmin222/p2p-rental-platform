package com.team7.rental.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.ReturnChecklistItem;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;

/**
 * 지정한 상태·기간의 대여를 바로 저장(커밋)한다. 피드 "대여 중" 배지(REQ-009)·기간 검색(REQ-010) 같은 조회 테스트용.
 * 상태만 바꾸고 handedOverAt 같은 시각 컬럼은 채우지 않는다.
 * 예: {@code rentalFixture.createRental(item, renter, RentalStatus.RENTING, start, end);}
 */
@TestComponent
public class RentalFixture {

	private final RentalRepository rentalRepository;
	private final ItemInspectionRepository itemInspectionRepository;

	RentalFixture(RentalRepository rentalRepository, ItemInspectionRepository itemInspectionRepository) {
		this.rentalRepository = rentalRepository;
		this.itemInspectionRepository = itemInspectionRepository;
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
		// 조회 테스트용이라 전이 경로를 거치지 않고 상태만 넣는다(인계·반납 기록은 createReturned)
		ReflectionTestUtils.setField(rental, "status", status);
		return rentalRepository.save(rental);
	}

	public Rental createPendingPayment(Item item, Member renter, LocalDate start, LocalDate end,
		LocalDateTime paymentExpiresAt) {
		return rentalRepository.save(Rental.createPendingPayment(item.getId(), item.getOwnerId(), renter.getId(),
			start, end, item.getPricePerDay(), item.getDeposit(), paymentExpiresAt));
	}

	/**
	 * 대여자가 returnedAt에 반납 요청한 RETURNED 대여와 판정 전 RETURN 기록(체크리스트 전부 true). 인계 기록·이력은 만들지 않는다.
	 */
	public Rental createReturned(Item item, Member renter, LocalDate start, LocalDate end, LocalDateTime returnedAt) {
		Rental rental = Rental.createReserved(item.getId(), item.getOwnerId(), renter.getId(), start, end,
			item.getPricePerDay(), item.getDeposit());
		ReflectionTestUtils.setField(rental, "status", RentalStatus.RENTING);
		rental.requestReturn(returnedAt);
		Rental saved = rentalRepository.save(rental);
		itemInspectionRepository.save(ItemInspection.returned(saved.getId(), renter.getId(),
			List.of("/files/inspections/fixture.jpg"), allChecked(), null));
		return saved;
	}

	public static Map<ReturnChecklistItem, Boolean> allChecked() {
		Map<ReturnChecklistItem, Boolean> checklist = new EnumMap<>(ReturnChecklistItem.class);
		for (ReturnChecklistItem item : ReturnChecklistItem.values()) {
			checklist.put(item, true);
		}
		return checklist;
	}
}
