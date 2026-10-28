package com.team7.rental.global.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;

/**
 * Redisson 분산 락(기본 전략). 키는 lock:item:{itemId}.
 * <p>
 * 임대 시간(leaseTime)을 지정하지 않는다. 지정하면 watchdog 자동 연장이 꺼져, 트랜잭션이 임대 시간을 넘기면 커밋 전에 락이 풀린다.
 * 대기 시간만 주면 락을 쥔 동안 watchdog이 연장하고, 서버가 죽으면 watchdog 타임아웃(기본 30초) 뒤 풀린다.
 */
@Component
public class RedissonRentalLockStrategy implements RentalLockStrategy {

	static final String KEY_PREFIX = "lock:item:";

	private final RedissonClient redissonClient;
	private final PolicyProperties policyProperties;

	public RedissonRentalLockStrategy(RedissonClient redissonClient, PolicyProperties policyProperties) {
		this.redissonClient = redissonClient;
		this.policyProperties = policyProperties;
	}

	@Override
	public <T> T executeWithItemLock(Long itemId, Supplier<T> task) {
		RLock lock = redissonClient.getLock(KEY_PREFIX + itemId);
		acquire(lock);
		try {
			return task.get();
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private void acquire(RLock lock) {
		boolean acquired;
		try {
			acquired = lock.tryLock(policyProperties.rental().lockWait().toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ErrorCode.RENTAL_LOCK_TIMEOUT);
		}
		if (!acquired) {
			throw new BusinessException(ErrorCode.RENTAL_LOCK_TIMEOUT);
		}
	}
}
