package com.team7.rental.item.repository;

import static com.team7.rental.item.entity.QItem.item;
import static com.team7.rental.item.entity.QItemAvailablePeriod.itemAvailablePeriod;
import static com.team7.rental.rental.entity.QRental.rental;
import static com.team7.rental.rental.entity.QRentalExtension.rentalExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.item.entity.ItemSortType;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.entity.MyItemTab;
import com.team7.rental.member.dto.RegionCenter;
import com.team7.rental.rental.entity.ExtensionStatus;
import com.team7.rental.rental.entity.QRental;
import com.team7.rental.rental.entity.RentalStatus;

import lombok.RequiredArgsConstructor;

/** 조회 전용 관계 쿼리: 대여 Repository/Service를 호출하지 않아 순환 의존을 만들지 않는다. */
@RequiredArgsConstructor
public class ItemListingRepositoryImpl implements ItemListingRepository {
	private static final char LIKE_ESCAPE = '!';

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
	public Page<ItemListRow> search(RegionCenter center, double radiusMeters, ItemSearchCondition condition,
		Pageable pageable, LocalDateTime now) {
		// AVAILABLE만: HIDDEN(REQ-044)·DELETED(REQ-008) 제외. 차단 사용자 제외(REQ-056)는 이 목록에 조건을 더한다.
		BooleanExpression[] conditions = {item.status.eq(ItemStatus.AVAILABLE), withinRange(center, radiusMeters),
			keywordMatches(condition.keyword()),
			condition.categoryId() == null ? null : item.categoryId.eq(condition.categoryId()),
			condition.minPrice() == null ? null : item.pricePerDay.goe(condition.minPrice()),
			condition.maxPrice() == null ? null : item.pricePerDay.loe(condition.maxPrice()),
			condition.hasPeriod() ? rentableFor(condition.startDate(), condition.endDate(), now) : null,
			!condition.hasPeriod() && condition.availableOnly() ? availableToday(now) : null};
		var content = queryFactory.select(projection(now)).from(item).where(conditions)
			.orderBy(searchOrder(condition)).offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(item.count()).from(item).where(conditions).fetchOne());
	}

	private BooleanExpression keywordMatches(String keyword) {
		if (keyword == null) {
			return null;
		}
		String pattern = containsPattern(keyword);
		return item.name.like(pattern, LIKE_ESCAPE).or(item.description.like(pattern, LIKE_ESCAPE));
	}

	/** 마지막 키는 항상 id라 같은 값끼리도 페이지 사이 순서가 고정된다. */
	private OrderSpecifier<?>[] searchOrder(ItemSearchCondition condition) {
		List<OrderSpecifier<?>> orders = new ArrayList<>();
		ItemSortType sort = condition.sort();
		if (sort == ItemSortType.PRICE_ASC) {
			orders.add(item.pricePerDay.asc());
		} else {
			// ACCURACY: 이름 일치 > 설명만 일치 > 최신순. 검색어가 없으면 LATEST와 같다.
			if (sort == ItemSortType.ACCURACY && condition.keyword() != null) {
				orders.add(new CaseBuilder().when(item.name.like(containsPattern(condition.keyword()), LIKE_ESCAPE))
					.then(0).otherwise(1).asc());
			}
			orders.add(item.bumpedAt.desc());
		}
		orders.add(item.id.desc());
		return orders.toArray(OrderSpecifier[]::new);
	}

	private static String containsPattern(String keyword) {
		// 백슬래시는 MySQL 문자열 이스케이프와 겹쳐 '!'를 LIKE 이스케이프 문자로 쓴다.
		String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

	/**
	 * [startDate, endDate](양 끝 포함)를 대여할 수 있는지. 예약 생성(RentalReservationTxService.create)과 같은 판정이다:
	 * 기간 전체가 가능 기간 하나에 들어가고(AvailablePeriod.isCovering), 점유 기간(RentalOccupancyReader)과 겹치지 않는다.
	 * 점유 조건을 바꾸면 RentalRepository.findOccupying·RentalExtensionRepository.findPendingOccupying과 함께 바꾼다.
	 */
	private BooleanExpression rentableFor(LocalDate startDate, LocalDate endDate, LocalDateTime now) {
		var covered = JPAExpressions.selectOne().from(itemAvailablePeriod)
			.where(itemAvailablePeriod.itemId.eq(item.id), itemAvailablePeriod.startDate.loe(startDate),
				itemAvailablePeriod.endDate.goe(endDate))
			.exists();
		return covered.and(occupied(startDate, endDate, now).not());
	}

	/**
	 * 기간 없는 availableOnly (open-issues #6): 오늘 이후 가능 기간이 남아 있고 오늘이 점유되지 않았으며, 대여 중(RENTING·OVERDUE,
	 * 피드 "대여 중" 배지와 같은 조건)이 아님. 종료일이 지난 미반납 OVERDUE는 날짜로는 오늘을 점유하지 않아 따로 뺀다.
	 */
	private BooleanExpression availableToday(LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		var remaining = JPAExpressions.selectOne().from(itemAvailablePeriod)
			.where(itemAvailablePeriod.itemId.eq(item.id), itemAvailablePeriod.endDate.goe(today))
			.exists();
		return remaining.and(occupied(today, today, now).not()).and(renting().not());
	}

	/** RentalOccupancyReader와 같은 점유: ACTIVE_FOR_PERIOD 대여(결제 대기는 만료 전) + 만료 전 결제 대기 연장 기간 */
	private BooleanExpression occupied(LocalDate from, LocalDate to, LocalDateTime now) {
		var rentals = JPAExpressions.selectOne().from(rental)
			.where(rental.itemId.eq(item.id), rental.status.in(RentalStatus.ACTIVE_FOR_PERIOD),
				rental.startDate.loe(to), rental.endDate.goe(from),
				rental.status.ne(RentalStatus.PENDING_PAYMENT).or(rental.paymentExpiresAt.gt(now)))
			.exists();
		// 연장 기간은 [prevEndDate + 1, newEndDate]라 시작 조건은 prevEndDate < to
		var extended = new QRental("extendedRental");
		var extensions = JPAExpressions.selectOne().from(rentalExtension, extended)
			.where(extended.id.eq(rentalExtension.rentalId), extended.itemId.eq(item.id),
				rentalExtension.status.eq(ExtensionStatus.PENDING_PAYMENT),
				rentalExtension.expiresAt.gt(now), rentalExtension.prevEndDate.lt(to),
				rentalExtension.newEndDate.goe(from))
			.exists();
		return rentals.or(extensions);
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
