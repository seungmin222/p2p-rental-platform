package com.team7.rental.payment.repository;

import static com.team7.rental.payment.entity.QPayment.payment;
import static com.team7.rental.rental.entity.QRental.rental;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.payment.dto.PaymentHistoryType;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/**
 * 결제·보증금 내역 (REQ-033) QueryDSL 조회 전용 빈. 대여자 조건만 rentals에서 본다(대여 Repository/Service를 부르지 않아 순환 의존이 없다).
 * PaymentRepository와 분리해 읽기 전용 PaymentHistoryQueryService만 주입한다: 결제 쓰기 트랜잭션 서비스가 rentals를 읽는 쿼리에 닿지 않게 하기 위해서다.
 */
@Repository
@RequiredArgsConstructor
public class PaymentHistoryQueryRepository {

	private final JPAQueryFactory queryFactory;

	/**
	 * 대여자 본인의 결제 행. createdAt ∈ [from, toExclusive), 최신 순(createdAt, id 내림차순). READY·FAILED는 뺀다.
	 *
	 * @param type open-issues #44 임시 해석
	 */
	public Page<PaymentHistoryRow> findRenterPayments(Long renterId, PaymentHistoryType type, LocalDateTime from,
		LocalDateTime toExclusive, Pageable pageable) {
		BooleanExpression[] conditions = {ownedBy(renterId), payment.createdAt.goe(from),
			payment.createdAt.lt(toExclusive), payment.status.notIn(PaymentStatus.READY, PaymentStatus.FAILED),
			typeOf(type)};
		List<PaymentHistoryRow> content = queryFactory.select(Projections.constructor(PaymentHistoryRow.class,
			payment.id, payment.rentalId, payment.type, payment.amount, payment.rentalFeeAmount,
			payment.depositAmount, payment.status, payment.depositStatus, payment.refundedAmount,
			payment.refundPendingAmount, payment.damageGrade, payment.deductionAmount, payment.deductionReason,
			payment.createdAt))
			.from(payment)
			.where(conditions)
			.orderBy(payment.createdAt.desc(), payment.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(payment.count()).from(payment).where(conditions).fetchOne());
	}

	private BooleanExpression ownedBy(Long renterId) {
		return payment.rentalId.in(JPAExpressions.select(rental.id).from(rental).where(rental.renterId.eq(renterId)));
	}

	private BooleanExpression typeOf(PaymentHistoryType type) {
		return switch (type) {
			// READY·FAILED는 공통 조건에서 뺐으므로 남은 행은 모두 승인된 결제다
			case ALL, PAYMENT -> null;
			case REFUND -> payment.refundedAmount.gt(0).or(payment.refundPendingAmount.isNotNull())
				.and(payment.rentalId.in(JPAExpressions.select(rental.id).from(rental)
					.where(rental.status.eq(RentalStatus.CANCELED))));
			case DEPOSIT -> payment.depositStatus.in(DepositStatus.HELD, DepositStatus.REFUNDED,
				DepositStatus.FORFEITED, DepositStatus.REFUND_FAILED);
		};
	}
}
