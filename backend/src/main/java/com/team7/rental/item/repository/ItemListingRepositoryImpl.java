package com.team7.rental.item.repository;

import static com.team7.rental.item.entity.QItem.item;
import static com.team7.rental.rental.entity.QRental.rental;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.entity.MyItemTab;
import com.team7.rental.member.dto.RegionCenter;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/** 조회 전용 관계 쿼리: 대여 Repository/Service를 호출하지 않아 순환 의존을 만들지 않는다. */
@RequiredArgsConstructor
public class ItemListingRepositoryImpl implements ItemListingRepository {
	private final JPAQueryFactory queryFactory;

	@Override
	public List<ItemListRow> findFeed(RegionCenter center, double radiusMeters, Long categoryId,
		ItemFeedCursor cursor, int limit, LocalDateTime now) {
		return queryFactory.select(projection(now)).from(item)
			.where(item.status.eq(ItemStatus.AVAILABLE), withinRange(center, radiusMeters),
				categoryId == null ? null : item.categoryId.eq(categoryId), before(cursor))
			.orderBy(item.bumpedAt.desc(), item.id.desc()).limit(limit).fetch();
	}

	@Override
	public Page<ItemListRow> findMyItems(Long memberId, MyItemTab tab, Pageable pageable, LocalDateTime now) {
		// 숨기기 REQ-044 전에는 계약대로 빈 탭을 유지한다.
		if (tab == MyItemTab.HIDDEN) {
			return Page.empty(pageable);
		}
		BooleanExpression[] conditions = {item.ownerId.eq(memberId), item.status.eq(ItemStatus.AVAILABLE),
			tab == MyItemTab.RENTING ? renting() : renting().not()};
		var content = queryFactory.select(projection(now)).from(item).where(conditions)
			.orderBy(item.id.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(item.count()).from(item).where(conditions).fetchOne());
	}

	private ConstructorExpression<ItemListRow> projection(LocalDateTime now) {
		var upcoming = JPAExpressions.select(rental.count()).from(rental)
			.where(rental.itemId.eq(item.id), rental.startDate.goe(now.toLocalDate()),
				rental.status.eq(RentalStatus.RESERVED).or(rental.status.eq(RentalStatus.PENDING_PAYMENT)
					.and(rental.paymentExpiresAt.gt(now))));
		return Projections.constructor(ItemListRow.class, item.id, item.regionId, item.name, item.pricePerDay,
			item.deposit, item.status, item.viewCount, item.wishCount, item.chatCount, item.bumpedAt, renting(),
			upcoming);
	}

	private BooleanExpression renting() {
		return JPAExpressions.selectOne().from(rental).where(rental.itemId.eq(item.id),
			rental.status.in(RentalStatus.RENTING, RentalStatus.OVERDUE)).exists();
	}

	private BooleanExpression before(ItemFeedCursor cursor) {
		return cursor == null ? null : item.bumpedAt.lt(cursor.bumpedAt())
			.or(item.bumpedAt.eq(cursor.bumpedAt()).and(item.id.lt(cursor.itemId())));
	}

	private BooleanExpression withinRange(RegionCenter center, double meters) {
		// MySQL ST_Distance_Sphere(point, point)의 기본 구 반지름(미터).
		double angular = meters / 6370986.0;
		double latDelta = Math.toDegrees(angular);
		double minLat = Math.max(-90, center.latitude() - latDelta);
		double maxLat = Math.min(90, center.latitude() + latDelta);
		BooleanExpression box = item.latitude.between(minLat, maxLat);
		if (minLat > -90 && maxLat < 90) {
			double lonDelta = Math
				.toDegrees(Math.asin(Math.min(1, Math.sin(angular) / Math.cos(Math.toRadians(center.latitude())))));
			double minLon = center.longitude() - lonDelta;
			double maxLon = center.longitude() + lonDelta;
			if (minLon < -180) {
				box = box.and(item.longitude.goe(minLon + 360).or(item.longitude.loe(maxLon)));
			} else if (maxLon > 180) {
				box = box.and(item.longitude.goe(minLon).or(item.longitude.loe(maxLon - 360)));
			} else {
				box = box.and(item.longitude.between(minLon, maxLon));
			}
		}
		// MySQL은 -180 대신 동치인 +180을 요구한다.
		var longitude = new com.querydsl.core.types.dsl.CaseBuilder().when(item.longitude.eq(-180.0))
			.then(180.0).otherwise(item.longitude);
		var distance = Expressions.numberTemplate(Double.class,
			"cast(function('st_distance_sphere', function('point', {0}, {1}), function('point', {2}, {3})) as double)",
			longitude, item.latitude, center.longitude() == -180.0 ? 180.0 : center.longitude(), center.latitude());
		return box.and(distance.loe(meters));
	}
}
