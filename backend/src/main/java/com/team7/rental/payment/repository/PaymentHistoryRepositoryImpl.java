package com.team7.rental.payment.repository;

import static com.team7.rental.payment.entity.QPayment.payment;
import static com.team7.rental.rental.entity.QRental.rental;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.payment.dto.PaymentHistoryType;
import com.team7.rental.payment.entity.DepositStatus;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/** 조회 전용 관계 쿼리: 대여자 조건만 rentals에서 본다(대여 Repository/Service를 부르지 않아 순환 의존이 없다. ItemListingRepositoryImpl과 같다) */
@RequiredArgsConstructor
public class PaymentHistoryRepositoryImpl implements PaymentHistoryRepository {

	private final JPAQueryFactory queryFactory;

	@Override
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
