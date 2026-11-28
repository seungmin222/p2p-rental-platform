package com.team7.rental.admin.repository;

import static com.team7.rental.admin.entity.QInquiry.*;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InquiryAdminSearchRepositoryImpl implements InquiryAdminSearchRepository {

	private final JPAQueryFactory queryFactory;

	/** 관리자 화면 하나가 쓰는 낮은 빈도 조회라 전용 인덱스 없이 PK 순으로 훑는다 */
	@Override
	public Page<Inquiry> searchForAdmin(InquiryStatus status, InquiryType type, Pageable pageable) {
		BooleanExpression[] conditions = {statusEq(status), typeEq(type)};
		List<Inquiry> content = queryFactory.selectFrom(inquiry)
			.where(conditions)
			.orderBy(inquiry.id.asc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();
		return PageableExecutionUtils.getPage(content, pageable,
			() -> queryFactory.select(inquiry.count()).from(inquiry).where(conditions).fetchOne());
	}

	private BooleanExpression statusEq(InquiryStatus status) {
		return status == null ? null : inquiry.status.eq(status);
	}

	private BooleanExpression typeEq(InquiryType type) {
		return type == null ? null : inquiry.type.eq(type);
	}
}
