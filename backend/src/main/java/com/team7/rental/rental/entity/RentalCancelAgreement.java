package com.team7.rental.rental.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

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
 * 시작일 이후·인계 전 양측 동의 취소 요청 (REQ-015). 진행 중 = REQUESTED이고 expiresAt > now.
 * 만료 배치가 늦어도 판단이 바뀌지 않게 상태만이 아니라 시각까지 본다.
 * <p>
 * {@code @Version}을 두지 않는다. 만료는 벌크 UPDATE(status = REQUESTED 조건)라 버전을 올리지 않고, 만료 전에 확인을 통과한 응답이
 * 이기도록 정했다(docs/prompts/feat/rental-cancel/notes.md). 응답끼리는 대여 행 락으로 직렬화된다.
 */
@Getter
@Entity
@Table(name = "rental_cancel_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalCancelAgreement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Column(nullable = false)
	private Long requestedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CancelReason reason;

	@Column(length = 200)
	private String reasonText;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CancelAgreementStatus status;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	/** 수락·거절 시각. 만료면 null */
	private LocalDateTime respondedAt;

	/**
	 * @param expiresAt 서비스가 Clock + cancel-agreement.expire-hours로 계산
	 */
	public static RentalCancelAgreement request(Long rentalId, Long requestedBy, CancelReason reason,
		String reasonText, LocalDateTime expiresAt) {
		RentalCancelAgreement agreement = new RentalCancelAgreement();
		agreement.rentalId = rentalId;
		agreement.requestedBy = requestedBy;
		agreement.reason = reason;
		agreement.reasonText = reasonText;
		agreement.status = CancelAgreementStatus.REQUESTED;
		agreement.expiresAt = expiresAt;
		return agreement;
	}

	/** REQUESTED이고 만료 시각 전. 정확히 만료 시각이면 닫힌 것으로 본다 */
	public boolean isOpen(LocalDateTime now) {
		return status == CancelAgreementStatus.REQUESTED && expiresAt.isAfter(now);
	}

	public boolean isRequestedBy(Long memberId) {
		return requestedBy.equals(memberId);
	}

	/**
	 * @throws BusinessException RENTAL_CANCEL_AGREEMENT_CLOSED: 이미 응답했거나 만료됐을 때
	 */
	public void accept(LocalDateTime now) {
		respond(CancelAgreementStatus.ACCEPTED, now);
	}

	/**
	 * @throws BusinessException RENTAL_CANCEL_AGREEMENT_CLOSED: 이미 응답했거나 만료됐을 때
	 */
	public void reject(LocalDateTime now) {
		respond(CancelAgreementStatus.REJECTED, now);
	}

	private void respond(CancelAgreementStatus result, LocalDateTime now) {
		if (!isOpen(now)) {
			throw new BusinessException(ErrorCode.RENTAL_CANCEL_AGREEMENT_CLOSED);
		}
		this.status = result;
		this.respondedAt = now;
	}
}
