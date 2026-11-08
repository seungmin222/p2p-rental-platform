package com.team7.rental.payment.entity;

import java.time.LocalDate;
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
 * 제공자 정산 원장 (REQ-039). 대여당 하나이고 실제 송금은 하지 않는다. {@code [생성] → SCHEDULED → PAID}(state-machines.md §3).
 * SCHEDULED ⇄ HELD는 문의(REQ-063)를 만들 때 메서드를 더한다. 지급 배치는 SCHEDULED만 고르므로 HELD가 생겨도 지급되지 않는다.
 */
@Getter
@Entity
@Table(name = "settlements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Column(nullable = false)
	private Long ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SettlementSource source;

	@Column(nullable = false)
	private int grossAmount;

	@Column(nullable = false)
	private int feeAmount;

	@Column(nullable = false)
	private int deductionAmount;

	@Column(nullable = false)
	private int payoutAmount;

	@Column(nullable = false)
	private LocalDate scheduledDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SettlementStatus status;

	private LocalDateTime paidAt;

	/** 지급 예정(SCHEDULED)으로 만든다 */
	public static Settlement schedule(Long rentalId, Long ownerId, SettlementSource source, SettlementAmount amount,
		LocalDate scheduledDate) {
		Settlement settlement = new Settlement();
		settlement.rentalId = rentalId;
		settlement.ownerId = ownerId;
		settlement.source = source;
		settlement.grossAmount = amount.gross();
		settlement.feeAmount = amount.fee();
		settlement.deductionAmount = amount.deduction();
		settlement.payoutAmount = amount.payout();
		settlement.scheduledDate = scheduledDate;
		settlement.status = SettlementStatus.SCHEDULED;
		return settlement;
	}

	/** 지급 배치 대상인지: SCHEDULED이고 scheduledDate ≤ today */
	public boolean isPayable(LocalDate today) {
		return status == SettlementStatus.SCHEDULED && !scheduledDate.isAfter(today);
	}

	/**
	 * SCHEDULED → PAID (지급 배치). 실제 송금은 없다.
	 *
	 * @throws IllegalStateException 지급 대상이 아닐 때. 배치는 {@link #isPayable}로 먼저 거른다
	 */
	public void pay(LocalDateTime now) {
		if (!isPayable(now.toLocalDate())) {
			throw new IllegalStateException("지급할 수 없는 정산입니다: id=" + id + ", status=" + status);
		}
		this.status = SettlementStatus.PAID;
		this.paidAt = now;
	}
}
