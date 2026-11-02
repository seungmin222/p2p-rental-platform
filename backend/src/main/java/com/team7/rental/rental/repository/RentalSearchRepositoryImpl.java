package com.team7.rental.rental.repository;

import static com.team7.rental.rental.entity.QRental.*;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RentalSearchRepositoryImpl implements RentalSearchRepository {

	private final JPAQueryFactory queryFactory;

	@Override
	public Page<RentalHistoryRow> findRenterHistory(Long renterId, Collection<RentalStatus> statuses,
		Pageable pageable) {
		BooleanExpression[] conditions = {rental.renterId.eq(renterId), rental.status.in(statuses)};
		List<RentalHistoryRow> content = queryFactory
			.select(Projections.constructor(RentalHistoryRow.class, rental.id, rental.status, rental.itemId,
				rental.ownerId, rental.startDate, rental.endDate, rental.rentalFee, rental.deposit,
				rental.completedAt))
			.from(rental)
			.where(conditions)
			.orderBy(overdueFirst(), rental.startDate.desc(), rental.id.desc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		// count에는 정렬을 넣지 않는다. 첫 페이지가 덜 찼거나 마지막 페이지면 PageableExecutionUtils가 count를 생략한다
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(rental.count()).from(rental).where(conditions).fetchOne());
	}

	@Override
	public Page<ItemRentalRow> findItemHistory(Long itemId, Pageable pageable) {
		var active = rental.status.in(RentalStatus.PENDING_PAYMENT, RentalStatus.RESERVED,
			RentalStatus.RENTING, RentalStatus.OVERDUE, RentalStatus.RETURNED);
		var groupOrder = new CaseBuilder().when(active).then(0).otherwise(1);
		var activeDate = new CaseBuilder().when(active).then(rental.startDate)
			.otherwise(com.querydsl.core.types.dsl.Expressions.nullExpression(java.time.LocalDate.class));
		var pastDate = new CaseBuilder().when(active.not()).then(rental.startDate)
			.otherwise(com.querydsl.core.types.dsl.Expressions.nullExpression(java.time.LocalDate.class));
		var content = queryFactory.select(Projections.constructor(ItemRentalRow.class,
			rental.id, rental.status, rental.renterId, rental.startDate, rental.endDate))
			.from(rental).where(rental.itemId.eq(itemId))
			.orderBy(groupOrder.asc(), activeDate.asc(), pastDate.desc(), rental.id.asc())
			.offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(rental.count()).from(rental).where(rental.itemId.eq(itemId)).fetchOne());
	}

	/** 연체 건을 목록 최상단에 (rental.md REQ-019). 모든 탭에 같은 정렬을 쓴다 */
	private OrderSpecifier<Integer> overdueFirst() {
		return new CaseBuilder()
			.when(rental.status.eq(RentalStatus.OVERDUE)).then(0)
			.otherwise(1)
			.asc();
	}
}
