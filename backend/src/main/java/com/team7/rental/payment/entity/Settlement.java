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
 * {@code SCHEDULED ⇄ HELD}는 분쟁 접수/종료(REQ-063). 지급 배치는 SCHEDULED만 고르므로 HELD는 지급되지 않는다.
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

	/**
	 * SCHEDULED → HELD (분쟁 접수, REQ-063). 지급 예정일은 그대로 두어, 풀리면 예정일이 지났어도 다음 지급 배치가 지급한다.
	 *
	 * @throws IllegalStateException SCHEDULED가 아닐 때. 호출하는 쪽이 {@link #isHoldable}로 먼저 거른다
	 */
	public void hold() {
		if (!isHoldable()) {
			throw new IllegalStateException("보류할 수 없는 정산입니다: id=" + id + ", status=" + status);
		}
		this.status = SettlementStatus.HELD;
	}

	/** 분쟁으로 보류할 수 있는지: SCHEDULED. 이미 HELD(다른 분쟁)이거나 PAID면 그대로 둔다 */
	public boolean isHoldable() {
		return status == SettlementStatus.SCHEDULED;
	}

	/**
	 * HELD → SCHEDULED (분쟁 종료). 부르는 곳은 관리자 분쟁 처리(REQ-057)에서 만든다. 대여에 열린 분쟁이 모두 끝났을 때만 부른다.
	 *
	 * @throws IllegalStateException HELD가 아닐 때
	 */
	public void release() {
		if (status != SettlementStatus.HELD) {
			throw new IllegalStateException("보류 중인 정산이 아닙니다: id=" + id + ", status=" + status);
		}
		this.status = SettlementStatus.SCHEDULED;
	}
}
