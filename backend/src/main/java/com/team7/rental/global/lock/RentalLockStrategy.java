package com.team7.rental.global.lock;

import java.util.function.Supplier;

/**
 * 물품 단위로 예약·연장·대여 가능 기간 수정을 직렬화한다(docs/domains/rental.md REQ-013 원자적 처리).
 * 동시성 비교 실험(docs/experiments/concurrency.md)에서 구현을 바꿔 끼울 수 있게 인터페이스로 둔다.
 */
public interface RentalLockStrategy {

	/**
	 * 물품 락을 잡고 task를 실행한 뒤 해제한다. task는 트랜잭션을 가진 별도 빈의 메서드여야 커밋이 해제보다 먼저 일어난다.
	 *
	 * @throws com.team7.rental.global.error.BusinessException RENTAL_LOCK_TIMEOUT: 대기 시간 안에 락을 못 잡았을 때
	 */
	<T> T executeWithItemLock(Long itemId, Supplier<T> task);
}
