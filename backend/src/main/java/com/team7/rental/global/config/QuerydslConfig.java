package com.team7.rental.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 동적 목록 조회용 JPAQueryFactory 빈 하나(backend/CLAUDE.md "쿼리 작성 방식"). QueryDSL은 조회 전용이다.
 * 주입되는 EntityManager는 스프링의 공유 프록시라 요청(트랜잭션)마다 현재 영속성 컨텍스트로 연결된다.
 */
@Configuration
public class QuerydslConfig {

	@Bean
	public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
		return new JPAQueryFactory(entityManager);
	}
}
