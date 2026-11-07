package com.team7.rental.payment.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * 보증금 차감 협의 이력 (REQ-049). 제안·거절·수락·자동 수락·관리자 확정을 모두 남긴다. 확정되면 값이 payments에 한 번 복사된다.
 * 응답(수락·거절·자동 수락)끼리와 새 제안은 대여 행 락으로 한 줄로 선다.
 * <p>
 * 열려 있음 = PROPOSED이고 응답 기한(createdAt + deduction.renter-response-hours) 전. 기한이 지나면 자동 수락 배치가 돌기 전이어도 대여자는
 * 수락·거절할 수 없다(결과가 배치 실행 시각에 좌우되지 않게).
 */
@Getter
@Entity
@Table(name = "deduction_proposals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeductionProposal extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	/** 제안한 제공자. ADMIN_DECIDED면 확정한 관리자 */
	@Column(nullable = false)
	private Long proposedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DamageGrade grade;

	@Column(nullable = false)
	private int amount;

	@Column(nullable = false, length = 500)
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> evidenceUrls;

	/** 견적서·영수증·구매 내역. 없으면 null */
	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> receiptUrls;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DeductionProposalStatus status;

	private LocalDateTime respondedAt;

	/** 제공자 제안 (PROPOSED). 상한·증빙 검증은 호출하는 쪽이 한다 */
	public static DeductionProposal propose(Long rentalId, Long proposedBy, DamageGrade grade, int amount,
		String reason, List<String> evidenceUrls, List<String> receiptUrls) {
		DeductionProposal proposal = create(rentalId, proposedBy, grade, amount, reason, evidenceUrls, receiptUrls);
		proposal.status = DeductionProposalStatus.PROPOSED;
		return proposal;
	}

	/** 관리자 확정 (ADMIN_DECIDED). 증빙 없이 바로 응답 시각까지 기록한다 */
	public static DeductionProposal adminDecide(Long rentalId, Long adminId, DamageGrade grade, int amount,
		String reason, LocalDateTime now) {
		DeductionProposal proposal = create(rentalId, adminId, grade, amount, reason, List.of(), List.of());
		proposal.status = DeductionProposalStatus.ADMIN_DECIDED;
		proposal.respondedAt = now;
		return proposal;
	}

	private static DeductionProposal create(Long rentalId, Long proposedBy, DamageGrade grade, int amount,
		String reason, List<String> evidenceUrls, List<String> receiptUrls) {
		if (amount < 0) {
			throw new IllegalArgumentException("차감액은 0원 이상이어야 합니다: " + amount);
		}
		DeductionProposal proposal = new DeductionProposal();
		proposal.rentalId = Objects.requireNonNull(rentalId, "rentalId");
		proposal.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy");
		proposal.grade = Objects.requireNonNull(grade, "grade");
		proposal.amount = amount;
		proposal.reason = Objects.requireNonNull(reason, "reason");
		proposal.evidenceUrls = List.copyOf(evidenceUrls);
		proposal.receiptUrls = receiptUrls.isEmpty() ? null : List.copyOf(receiptUrls);
		return proposal;
	}

	/** 대여자 응답 기한 */
	public LocalDateTime responseDeadline(int renterResponseHours) {
		return getCreatedAt().plusHours(renterResponseHours);
	}

	/** PROPOSED이고 응답 기한 전. 정확히 기한이면 닫힌 것으로 본다 */
	public boolean isOpen(LocalDateTime now, int renterResponseHours) {
		return status == DeductionProposalStatus.PROPOSED && responseDeadline(renterResponseHours).isAfter(now);
	}

	/** 자동 수락 대상: PROPOSED이고 응답 기한이 지났다 */
	public boolean isAutoAcceptDue(LocalDateTime now, int renterResponseHours) {
		return status == DeductionProposalStatus.PROPOSED && !responseDeadline(renterResponseHours).isAfter(now);
	}

	/**
	 * @throws BusinessException DEDUCTION_PROPOSAL_CLOSED: 이미 응답했거나 응답 기한이 지났을 때
	 */
	public void accept(LocalDateTime now, int renterResponseHours) {
		respond(DeductionProposalStatus.ACCEPTED, now, renterResponseHours);
	}

	/**
	 * @throws BusinessException DEDUCTION_PROPOSAL_CLOSED: 이미 응답했거나 응답 기한이 지났을 때
	 */
	public void reject(LocalDateTime now, int renterResponseHours) {
		respond(DeductionProposalStatus.REJECTED, now, renterResponseHours);
	}

	/**
	 * 72시간 무응답 자동 수락.
	 *
	 * @throws BusinessException DEDUCTION_PROPOSAL_CLOSED: 대상이 아닐 때
	 */
	public void autoAccept(LocalDateTime now, int renterResponseHours) {
		if (!isAutoAcceptDue(now, renterResponseHours)) {
			throw new BusinessException(ErrorCode.DEDUCTION_PROPOSAL_CLOSED);
		}
		this.status = DeductionProposalStatus.AUTO_ACCEPTED;
		this.respondedAt = now;
	}

	private void respond(DeductionProposalStatus to, LocalDateTime now, int renterResponseHours) {
		if (!isOpen(now, renterResponseHours)) {
			throw new BusinessException(ErrorCode.DEDUCTION_PROPOSAL_CLOSED);
		}
		this.status = to;
		this.respondedAt = now;
	}
}
