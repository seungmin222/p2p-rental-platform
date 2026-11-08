package com.team7.rental.payment.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementAmount;
import com.team7.rental.payment.entity.SettlementSource;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.service.RentalSettlementPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 제공자 정산 생성 (REQ-039). 대여 완료·수수료 취소 트랜잭션에 참여한다({@link RentalSettlementPort}). 호출하는 쪽이 대여 행 락을 잡고 있어 같은 대여의 생성은
 * 한 줄로 서고, 이미 있으면 건너뛴다(uk_settlements_rental_id가 마지막 방어선).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SettlementTxService implements RentalSettlementPort {

	/** 돈을 받은 결제: 승인됐고 전액 취소되지 않았다. 취소 수수료 건은 취소 트랜잭션 안에서 부르므로 아직 APPROVED다 */
	private static final Set<PaymentStatus> COLLECTED = EnumSet.of(PaymentStatus.APPROVED,
		PaymentStatus.PARTIAL_CANCELED);

	private final SettlementRepository settlementRepository;
	private final PaymentRepository paymentRepository;
	private final PolicyProperties policyProperties;

	@Override
	public void createForCompletion(Long rentalId, Long ownerId, int overdueFee, LocalDateTime completedAt) {
		if (settlementRepository.existsByRentalId(rentalId)) {
			return;
		}
		Optional<Payment> collected = findCollectedRentalPayment(rentalId);
		if (collected.isEmpty()) {
			return;
		}
		Payment payment = collected.get();
		int deduction = payment.getDeductionAmount() == null ? 0 : payment.getDeductionAmount();
		SettlementAmount amount = SettlementAmount.ofCompletion(payment.getRentalFeeAmount(),
			sumExtensionFees(rentalId), overdueFee, deduction, policyProperties.settlement().feeRatePercent());
		save(rentalId, ownerId, SettlementSource.COMPLETED, amount, completedAt);
	}

	@Override
	public void createForCancelFee(Long rentalId, Long ownerId, int cancelFee, LocalDateTime canceledAt) {
		if (cancelFee <= 0 || settlementRepository.existsByRentalId(rentalId)
			|| findCollectedRentalPayment(rentalId).isEmpty()) {
			return;
		}
		save(rentalId, ownerId, SettlementSource.CANCEL_FEE,
			SettlementAmount.ofCancelFee(cancelFee, policyProperties.settlement().feeRatePercent()), canceledAt);
	}

	private void save(Long rentalId, Long ownerId, SettlementSource source, SettlementAmount amount,
		LocalDateTime baseAt) {
		if (amount.payout() == 0) {
			return;
		}
		settlementRepository.save(Settlement.schedule(rentalId, ownerId, source, amount,
			BusinessDays.plus(baseAt.toLocalDate(), policyProperties.settlement().payoutDelayBusinessDays())));
	}

	/** 일반 읽기. 호출하는 쪽이 대여 행 락을 잡았다(PaymentRepository 주석: 행이 없을 때 FOR UPDATE의 갭 락을 피한다) */
	private Optional<Payment> findCollectedRentalPayment(Long rentalId) {
		return paymentRepository.findAllByRentalIdAndTypeOrderByIdDesc(rentalId, PaymentType.RENTAL).stream()
			.filter(payment -> COLLECTED.contains(payment.getStatus()))
			.findFirst();
	}

	/** 승인된 연장 결제 합계. 연장(REQ-038) 전에는 EXTENSION 결제가 없어 0이다 */
	private long sumExtensionFees(Long rentalId) {
		List<Payment> extensions = paymentRepository.findAllByRentalIdAndTypeOrderByIdDesc(rentalId,
			PaymentType.EXTENSION);
		return extensions.stream()
			.filter(payment -> payment.getStatus() == PaymentStatus.APPROVED)
			.mapToLong(Payment::getAmount)
			.sum();
	}
}
