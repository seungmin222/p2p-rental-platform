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

	/** 연체 건을 목록 최상단에 (rental.md REQ-019). 모든 탭에 같은 정렬을 쓴다 */
	private OrderSpecifier<Integer> overdueFirst() {
		return new CaseBuilder()
			.when(rental.status.eq(RentalStatus.OVERDUE)).then(0)
			.otherwise(1)
			.asc();
	}
}
