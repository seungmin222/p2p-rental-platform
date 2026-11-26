package com.team7.rental.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * JPQL(QueryDSL)에서 쓰는 MySQL 전용 함수. {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}로 등록한다.
 * <ul>
 * <li>{@code match_against(name, description, phrase)}: 물품 키워드 검색의 FULLTEXT(ngram) 조건
 * ({@code idx_items_name_description}, {@code docs/experiments/query-index.md}). 함수 호출 문법이 아니라 템플릿으로만 쓸 수 있어 등록한다.</li>
 * </ul>
 */
public class MySqlFunctionContributor implements FunctionContributor {
	@Override
	public void contributeFunctions(FunctionContributions contributions) {
		var doubleType = contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE);
		contributions.getFunctionRegistry()
			.registerPattern("match_against", "match(?1, ?2) against(?3 in boolean mode)", doubleType);
	}
}
