package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.payment.dto.HeldDepositResponse;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.entity.DamagePolicy;
import com.team7.rental.payment.entity.DeductionProposal;
import com.team7.rental.payment.entity.DeductionProposalStatus;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.DamagePolicyRepository;
import com.team7.rental.payment.repository.DeductionAutoAcceptTarget;
import com.team7.rental.payment.repository.DeductionProposalRepository;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.dto.RentalDepositView;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.service.RentalPaymentTxService;
import com.team7.rental.rental.service.RentalRefundPort.Completion;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/**
 * 보증금 보류·차감의 트랜잭션 부분 (REQ-049). PG 호출·사진 업로드·알림은 {@link DepositDeductionService}가 트랜잭션 밖에서 한다.
 * <p>
 * 모든 쓰기는 대여 행 락({@link RentalPaymentTxService#lockForDeposit}) → 결제 행 락 순서다(payment-core와 같음). 같은 대여의 제안·수락·거절·
 * 자동 수락·관리자 확정은 대여 행 락으로 한 줄로 서고, 늦은 쪽은 바뀐 상태를 보고 409(배치는 건너뜀)다. 제안 행은 그 뒤에 읽어 잠그지 않는다.
 * <p>
 * 차감 협의·확정은 보증금이 보류(HELD) 중이고 진행 중인 환불이 없을 때만 한다({@link Payment#isDepositSettleable}). 확정이 환불을 시작하면 이 조건이
 * 깨지므로 한 대여의 보증금 반환(멱등성 키 deposit-refund:{rentalId})은 정상 반환(FROZEN)이든 차감 확정(HELD)이든 한 번만 일어난다.
 * <p>
 * 상한은 대여 시점이 아니라 현재 물품 카테고리의 damage_policies로 계산한다(대여에 카테고리 스냅샷이 없다).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DepositDeductionTxService {

	private final PaymentRepository paymentRepository;
	private final DeductionProposalRepository deductionProposalRepository;
	private final DamagePolicyRepository damagePolicyRepository;
	private final RentalPaymentTxService rentalPaymentTxService;
	private final PaymentRefundTxService paymentRefundTxService;
	private final ItemQueryService itemQueryService;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 확정 결과. refund가 있으면 커밋 뒤 실행하고, 없으면 이미 COMPLETED다(반환액 0원 → FORFEITED).
	 */
	public record Settlement(Long rentalId, Long proposalId, Long ownerId, Long renterId, RefundTicket refund) {
	}

	/** 새 제안 결과 */
	public record Proposed(Long rentalId, Long proposalId, Long renterId) {
	}

	/** 거절 결과 */
	public record Rejected(Long rentalId, Long proposalId, Long ownerId) {
	}

	/**
	 * 등급별 차감 상한 조회. 당사자만.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, DEDUCTION_GRADE_NOT_ALLOWED
	 */
	@Transactional(readOnly = true)
	public DeductionLimit getLimit(Long rentalId, Long memberId, DamageGrade grade) {
		RentalDepositView view = rentalPaymentTxService.getDepositView(rentalId);
		requireParticipant(view, memberId);
		return limitOf(view, grade);
	}

	/**
	 * 제안 사전 검사(사진을 처리하기 전). {@link #propose}가 락을 잡고 다시 검사한다.
	 *
	 * @return 증빙 규칙을 정할 상한 정보
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION,
	 *     DEPOSIT_NOT_HELD, DEDUCTION_PROPOSAL_IN_PROGRESS, DEDUCTION_GRADE_NOT_ALLOWED, DEDUCTION_EXCEEDS_DEPOSIT,
	 *     DEDUCTION_EXCEEDS_CAP
	 */
	@Transactional(readOnly = true)
	public ProposeRule validatePropose(Long rentalId, Long memberId, DamageGrade grade, int amount) {
		RentalDepositView view = rentalPaymentTxService.getDepositView(rentalId);
		requireRole(view, memberId, RentalRole.OWNER);
		requireReturned(view);
		requireSettleable(paymentRepository.findAllByRentalIdAndTypeOrderByIdDesc(rentalId, PaymentType.RENTAL));
		return checkProposal(view, grade, amount);
	}

	/**
	 * @param lostReported 미반납 신고 건이면 증빙 사진 없이 제안할 수 있다(물건이 없어 찍을 수 없다)
	 * @param receiptRequired 견적서·영수증·구매 내역 필수
	 */
	public record ProposeRule(boolean lostReported, boolean receiptRequired) {
	}

	/**
	 * 제공자 제안 (PROPOSED). 증빙 장수는 호출하는 쪽이 {@link ProposeRule}로 검사했다.
	 *
	 * @throws BusinessException {@link #validatePropose}와 같음
	 */
	public Proposed propose(Long rentalId, Long memberId, DamageGrade grade, int amount, String reason,
		List<String> evidenceUrls, List<String> receiptUrls) {
		RentalDepositView view = rentalPaymentTxService.lockForDeposit(rentalId);
		requireRole(view, memberId, RentalRole.OWNER);
		requireReturned(view);
		requireSettleable(lockPayments(rentalId));
		checkProposal(view, grade, amount);
		DeductionProposal proposal = deductionProposalRepository.save(
			DeductionProposal.propose(rentalId, memberId, grade, amount, reason, evidenceUrls, receiptUrls));
		return new Proposed(rentalId, proposal.getId(), view.renterId());
	}

	/**
	 * 대여자 수락 → 확정 처리.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_RENTER_ONLY, RENTAL_INVALID_TRANSITION,
	 *     DEPOSIT_NOT_HELD, DEDUCTION_PROPOSAL_NOT_FOUND, DEDUCTION_PROPOSAL_CLOSED
	 */
	public Settlement accept(Long rentalId, Long memberId, Long proposalId) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalDepositView view = rentalPaymentTxService.lockForDeposit(rentalId);
		requireRole(view, memberId, RentalRole.RENTER);
		requireReturned(view);
		Payment payment = requireSettleable(lockPayments(rentalId));
		DeductionProposal proposal = getProposal(rentalId, proposalId);
		proposal.accept(now, renterResponseHours());
		return settle(view, payment, proposal, memberId, RentalStatusHistory.REASON_DEDUCTION_ACCEPTED, now);
	}

	/**
	 * 대여자 거절. 보류는 이어지고 제공자는 다시 제안할 수 있다.
	 *
	 * @throws BusinessException {@link #accept}와 같음
	 */
	public Rejected reject(Long rentalId, Long memberId, Long proposalId) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalDepositView view = rentalPaymentTxService.lockForDeposit(rentalId);
		requireRole(view, memberId, RentalRole.RENTER);
		requireReturned(view);
		requireSettleable(lockPayments(rentalId));
		DeductionProposal proposal = getProposal(rentalId, proposalId);
		proposal.reject(now, renterResponseHours());
		return new Rejected(rentalId, proposal.getId(), view.ownerId());
	}

	/** 자동 수락 배치 대상 (PROPOSED이고 deduction.renter-response-hours 경과) */
	@Transactional(readOnly = true)
	public List<DeductionAutoAcceptTarget> findAutoAcceptTargets(LocalDateTime now) {
		return deductionProposalRepository.findAutoAcceptTargets(now.minusHours(renterResponseHours()));
	}

	/**
	 * 자동 수락 한 건 → 확정 처리(이력 changedBy = null). 대상 조회 뒤 대여자가 응답했거나 다른 확정이 먼저 끝났으면 건너뛴다(멱등).
	 *
	 * @return 처리했으면 확정 결과
	 */
	public Optional<Settlement> autoAccept(DeductionAutoAcceptTarget target, LocalDateTime now) {
		RentalDepositView view = rentalPaymentTxService.lockForDeposit(target.rentalId());
		List<Payment> payments = lockPayments(target.rentalId());
		DeductionProposal proposal = deductionProposalRepository.findById(target.proposalId()).orElse(null);
		if (proposal == null || !proposal.isAutoAcceptDue(now, renterResponseHours())
			|| view.status() != RentalStatus.RETURNED) {
			return Optional.empty();
		}
		Optional<Payment> payment = payments.stream()
			.filter(Payment::isDepositSettleable)
			.findFirst();
		if (payment.isEmpty()) {
			return Optional.empty();
		}
		proposal.autoAccept(now, renterResponseHours());
		return Optional.of(settle(view, payment.get(), proposal, null,
			RentalStatusHistory.REASON_DEDUCTION_AUTO_ACCEPT_BATCH, now));
	}

	/**
	 * 관리자 확정 (ADMIN_DECIDED 행을 남기고 확정 처리). 보류 후 deposit.hold-agreement-days가 지나야 하고, 대여자 응답을 기다리는 제안이 있으면
	 * 그 결과(수락·거절·자동 수락)를 기다린다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_INVALID_TRANSITION, DEPOSIT_NOT_HELD,
	 *     DEPOSIT_ADMIN_DECISION_TOO_EARLY, DEDUCTION_PROPOSAL_IN_PROGRESS, DEDUCTION_GRADE_NOT_ALLOWED,
	 *     DEDUCTION_EXCEEDS_DEPOSIT, DEDUCTION_EXCEEDS_CAP
	 */
	public Settlement adminDecide(Long rentalId, Long adminId, DamageGrade grade, int amount, String reason) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalDepositView view = rentalPaymentTxService.lockForDeposit(rentalId);
		requireReturned(view);
		Payment payment = requireSettleable(lockPayments(rentalId));
		if (payment.getHeldAt().plusDays(policyProperties.deposit().holdAgreementDays()).isAfter(now)) {
			throw new BusinessException(ErrorCode.DEPOSIT_ADMIN_DECISION_TOO_EARLY);
		}
		checkProposal(view, grade, amount);
		DeductionProposal decision = deductionProposalRepository.save(
			DeductionProposal.adminDecide(rentalId, adminId, grade, amount, reason, now));
		return settle(view, payment, decision, adminId, RentalStatusHistory.REASON_DEDUCTION_ADMIN_DECIDED, now);
	}

	/** 관리자 확정 대상 목록: 보류 후 deposit.hold-agreement-days 경과, 보류가 오래된 순 */
	@Transactional(readOnly = true)
	public List<HeldDepositResponse> findHeld() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<Payment> held = paymentRepository.findHeldBefore(
			now.minusDays(policyProperties.deposit().holdAgreementDays()));
		List<Long> rentalIds = held.stream().map(Payment::getRentalId).toList();
		Map<Long, RentalDepositView> views = rentalPaymentTxService.getDepositViews(rentalIds);
		Map<Long, DeductionProposal> latest = rentalIds.isEmpty() ? Map.of()
			: deductionProposalRepository.findLatestByRentalIds(rentalIds).stream()
				.collect(Collectors.toMap(DeductionProposal::getRentalId, Function.identity()));
		return held.stream()
			.filter(payment -> views.containsKey(payment.getRentalId()))
			.map(payment -> {
				RentalDepositView view = views.get(payment.getRentalId());
				DeductionProposal proposal = latest.get(payment.getRentalId());
				return new HeldDepositResponse(view.rentalId(), view.itemId(), view.ownerId(), view.renterId(),
					view.deposit(), view.overdueFee(), view.remainingDeposit(), view.lostReported(),
					payment.getHeldAt(),
					proposal == null ? null : DeductionViews.proposalOf(proposal, renterResponseHours()));
			})
			.toList();
	}

	/**
	 * 확정 처리 (수락·자동 수락·관리자 확정 공통). payments에 확정 결과를 복사하고 반환액 = 보증금 − 연체료 − 차감액을 처리한다.
	 * <ul>
	 *   <li>반환액 &gt; 0: 부분 취소를 준비한다(멱등성 키 deposit-refund:{rentalId}). 대여는 RETURNED로 두고, 환불이 끝나면 COMPLETED
	 *   (PaymentRefundTxService#applyResult, 실패하면 재시도 배치)</li>
	 *   <li>반환액 = 0: PG 없이 FORFEITED, 바로 COMPLETED</li>
	 * </ul>
	 * 금액이 남은 보증금을 넘지 않는 것은 제안·관리자 확정 때 검사했다. RETURNED 이후 보증금·연체료가 바뀌지 않으므로 다시 검사하지 않는다
	 * (등급별 상한은 현재 카테고리로 계산해 제안 뒤 바뀔 수 있어, 합의된 금액을 수락 때 다시 깎지 않는다).
	 * <p>
	 * 불변식: payment.depositAmount == rental.deposit. RENTAL 결제는 {@code PaymentTxService#createOrder}에서만 만들어지고 대여 행 락 안에서
	 * rental.deposit을 그대로 복사하며, Rental.deposit은 생성 뒤 바뀌지 않는다. 그래서 상한 검사({@link #limitOf}, rental.deposit 기준)와
	 * 반환액 계산(payment.depositAmount 기준)이 같은 보증금을 본다. 보증금을 바꾸는 기능이 생기면 이 둘을 한쪽으로 맞춰야 한다.
	 */
	private Settlement settle(RentalDepositView view, Payment payment, DeductionProposal proposal, Long changedBy,
		String historyReason, LocalDateTime now) {
		int refund = payment.getDepositAmount() - view.overdueFee() - proposal.getAmount();
		if (refund < 0) {
			throw new IllegalStateException("차감액이 남은 보증금을 넘습니다: rentalId=" + view.rentalId());
		}
		payment.recordDeduction(proposal.getGrade(), proposal.getAmount(), proposal.getReason());
		Completion completion = new Completion(changedBy, historyReason);
		RefundTicket ticket = null;
		if (refund > 0) {
			ticket = paymentRefundTxService.prepare(payment, refund, RefundPurpose.DEPOSIT, completion);
		} else {
			payment.forfeitDeposit();
			// 반환할 금액이 없어 바로 완료한다. 제공자 정산(REQ-039)을 만들 자리다
			rentalPaymentTxService.completeAfterDepositReturn(view.rentalId(), completion, now);
		}
		return new Settlement(view.rentalId(), proposal.getId(), view.ownerId(), view.renterId(), ticket);
	}

	/** 등급·금액 검사. 대여자 응답을 기다리는 제안(PROPOSED, 기한이 지나 배치 대기 중인 것 포함)이 있으면 409 */
	private ProposeRule checkProposal(RentalDepositView view, DamageGrade grade, int amount) {
		if (deductionProposalRepository.existsByRentalIdAndStatus(view.rentalId(), DeductionProposalStatus.PROPOSED)) {
			throw new BusinessException(ErrorCode.DEDUCTION_PROPOSAL_IN_PROGRESS);
		}
		DeductionLimit limit = limitOf(view, grade);
		limit.validate(amount);
		return new ProposeRule(view.lostReported(), limit.receiptRequired());
	}

	private DeductionLimit limitOf(RentalDepositView view, DamageGrade grade) {
		if (grade == DamageGrade.A) {
			throw new BusinessException(ErrorCode.DEDUCTION_GRADE_NOT_ALLOWED);
		}
		DamagePolicy policy = damagePolicyRepository.findByCategoryIdAndGrade(
			itemQueryService.getCategoryId(view.itemId()), grade)
			.orElseThrow(() -> new IllegalStateException("카테고리의 차감 정책이 없습니다: itemId=" + view.itemId()));
		return DeductionLimit.of(policy, view.deposit(), view.overdueFee(), view.lostReported());
	}

	private DeductionProposal getProposal(Long rentalId, Long proposalId) {
		return deductionProposalRepository.findById(proposalId)
			.filter(proposal -> proposal.getRentalId().equals(rentalId))
			.orElseThrow(() -> new BusinessException(ErrorCode.DEDUCTION_PROPOSAL_NOT_FOUND));
	}

	private List<Payment> lockPayments(Long rentalId) {
		return paymentRepository.findAllByRentalIdForUpdate(rentalId, PaymentType.RENTAL);
	}

	private Payment requireSettleable(List<Payment> payments) {
		return payments.stream()
			.filter(Payment::isDepositSettleable)
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_NOT_HELD));
	}

	private void requireReturned(RentalDepositView view) {
		if (view.status() != RentalStatus.RETURNED) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	private void requireParticipant(RentalDepositView view, Long memberId) {
		if (!view.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
	}

	private void requireRole(RentalDepositView view, Long memberId, RentalRole role) {
		requireParticipant(view, memberId);
		if (view.roleOf(memberId) != role) {
			throw new BusinessException(
				role == RentalRole.OWNER ? ErrorCode.RENTAL_OWNER_ONLY : ErrorCode.RENTAL_RENTER_ONLY);
		}
	}

	private int renterResponseHours() {
		return policyProperties.deduction().renterResponseHours();
	}
}
