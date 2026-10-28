package com.team7.rental.rental.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대여 상태 변경 이력 (docs/state-machines.md 규칙). Rental을 저장해야 id가 생기므로, 이력 저장은 Rental 저장 뒤에 서비스가 맡는다.
 */
@Getter
@Entity
@Table(name = "rental_status_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalStatusHistory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	/** 생성 이력이면 null */
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private RentalStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RentalStatus toStatus;

	/** 시스템(배치)이 바꿨으면 null */
	private Long changedBy;

	/** 배치명 또는 변경 사유 */
	@Column(length = 255)
	private String reason;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	/**
	 * @param fromStatus 생성 이력이면 null
	 * @param changedBy 시스템이면 null, 이때 reason에 배치명
	 */
	public static RentalStatusHistory create(Long rentalId, RentalStatus fromStatus, RentalStatus toStatus,
		Long changedBy, String reason, LocalDateTime changedAt) {
		RentalStatusHistory history = new RentalStatusHistory();
		history.rentalId = rentalId;
		history.fromStatus = fromStatus;
		history.toStatus = toStatus;
		history.changedBy = changedBy;
		history.reason = reason;
		history.changedAt = changedAt;
		return history;
	}
}
