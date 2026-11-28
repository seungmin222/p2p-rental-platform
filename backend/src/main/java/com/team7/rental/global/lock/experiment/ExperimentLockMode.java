package com.team7.rental.global.lock.experiment;

/**
 * 동시성 비교 실험(docs/experiments/concurrency.md)에서 고르는 예약 직렬화 방식. 기본 경로에서는 쓰지 않는다.
 */
public enum ExperimentLockMode {

	/** 락 없음(대조군): 겹침 검사 + INSERT만 */
	NONE,

	/** 낙관적 락: 물품 version(부하 DB에만 있는 items.version)을 조건부 UPDATE. 0행이면 충돌 */
	OPTIMISTIC,

	/** 비관적 락: 트랜잭션 첫 문장으로 물품 행 SELECT ... FOR UPDATE */
	PESSIMISTIC_ITEM,

	/** 비관적 락: 그 물품의 활성 대여 행(겹침 검사가 읽는 범위)을 SELECT ... FOR UPDATE. REPEATABLE READ면 갭 락이 걸린다 */
	PESSIMISTIC_RANGE,

	/** Redisson 분산 락(기본 전략과 같은 락) + 바깥 트랜잭션의 격리 수준 */
	REDISSON,

	/** 락 없이 rental_dates UK(item_id, rented_date)로만 막는다 */
	UNIQUE,

	/** Redisson 분산 락 + rental_dates UK(2차 방어) */
	REDISSON_UNIQUE
}
