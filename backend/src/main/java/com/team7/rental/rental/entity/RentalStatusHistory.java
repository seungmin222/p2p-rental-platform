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

	/** 인계 확인 (REQ-017) */
	public static final String REASON_HANDOVER = "HANDOVER";
	/** 반납 요청·대리 접수 (REQ-018). 대리 접수는 changedBy가 제공자 */
	public static final String REASON_RETURN_REQUEST = "RETURN_REQUEST";
	/** 상태 확인 '정상' (REQ-018) */
	public static final String REASON_RETURN_CONFIRM_NORMAL = "RETURN_CONFIRM_NORMAL";
	/** 상태 확인 '이상'. 1순위는 기록만 하고 COMPLETED (REQ-018) */
	public static final String REASON_RETURN_CONFIRM_ABNORMAL = "RETURN_CONFIRM_ABNORMAL";
	/** 자동 상태 확인 배치 (REQ-018). changedBy = null */
	public static final String REASON_RETURN_AUTO_CONFIRM_BATCH = "RETURN_AUTO_CONFIRM_BATCH";
	/** 결제 승인 (REQ-024). changedBy = null */
	public static final String REASON_PAYMENT_APPROVED = "PAYMENT_APPROVED";
	/** 결제 만료 (REQ-024). 만료 배치와 승인 직후 만료 경합 모두. changedBy = null */
	public static final String REASON_PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
	/** 결제 금액 불일치 (REQ-024). changedBy = null */
	public static final String REASON_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
	/** 인계 없음 자동 취소 배치 (REQ-017). changedBy = null */
	public static final String REASON_NO_HANDOVER_BATCH = "NO_HANDOVER_BATCH";

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

	/** 취소 이력의 reason. "SCHEDULE_CHANGE", 텍스트가 있으면 "OTHER: {텍스트}" (코드 + 200자라 255에 들어간다) */
	public static String cancelReasonOf(CancelReason reason, String reasonText) {
		return reasonText == null ? reason.name() : reason.name() + ": " + reasonText;
	}

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
