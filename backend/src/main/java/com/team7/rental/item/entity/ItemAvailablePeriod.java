package com.team7.rental.item.entity;

import java.time.LocalDate;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대여 가능 기간 [startDate, endDate] (양 끝 포함). 같은 물품 안의 구간 겹침 검증은 REQ-006·007 서비스가 한다.
 */
@Getter
@Entity
@Table(name = "item_available_periods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemAvailablePeriod extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long itemId;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	public static ItemAvailablePeriod create(Long itemId, LocalDate startDate, LocalDate endDate) {
		ItemAvailablePeriod period = new ItemAvailablePeriod();
		period.itemId = itemId;
		period.startDate = startDate;
		period.endDate = endDate;
		return period;
	}

	/** [start, end]가 이 기간 안에 완전히 들어가는지 (REQ-013 RENTAL_OUT_OF_AVAILABLE_PERIOD 판단) */
	public boolean isCovering(LocalDate start, LocalDate end) {
		return !start.isBefore(startDate) && !end.isAfter(endDate);
	}
}
