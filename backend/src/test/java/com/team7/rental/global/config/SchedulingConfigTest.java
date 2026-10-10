package com.team7.rental.global.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.support.IntegrationTestSupport;

class SchedulingConfigTest extends IntegrationTestSupport {

	private static final Duration TASK_DURATION = Duration.ofSeconds(1);

	@Autowired
	private LockProvider lockProvider;

	@Autowired
	private LockedJob lockedJob;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	@DisplayName("같은 락 이름으로 동시에 두 번 실행하면 한 번만 실행된다")
	void sameLockRunsOnce() throws Exception {
		// given
		LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
		AtomicInteger executions = new AtomicInteger();
		Runnable attempt = () -> executor.executeWithLock((Runnable)() -> {
			executions.incrementAndGet();
			sleep(TASK_DURATION);
		}, new LockConfiguration(Instant.now(), "test.same-lock", Duration.ofMinutes(1), Duration.ZERO));

		// when
		runConcurrently(attempt, attempt);

		// then
		assertThat(executions).hasValue(1);
	}

	@Test
	@DisplayName("락을 쥔 동안 Redis 키에 프로필별 환경 접두사가 붙는다")
	void lockKeyHasEnvironmentPrefix() throws Exception {
		// given
		LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
		List<Boolean> keyExists = new ArrayList<>();

		// when
		executor.executeWithLock((Runnable)() -> keyExists.add(redisTemplate.hasKey("job-lock:test:test.key-prefix")),
			new LockConfiguration(Instant.now(), "test.key-prefix", Duration.ofMinutes(1), Duration.ZERO));

		// then
		assertThat(keyExists).containsExactly(true);
	}

	@Test
	@DisplayName("@SchedulerLock 메서드를 동시에 두 번 호출하면 한 번만 실행된다")
	void schedulerLockAnnotation() throws Exception {
		// when
		runConcurrently(lockedJob::run, lockedJob::run);

		// then
		assertThat(lockedJob.executions()).isEqualTo(1);
	}

	private static void runConcurrently(Runnable... tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
		CountDownLatch ready = new CountDownLatch(tasks.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = Arrays.stream(tasks).<Future<?>>map(task -> pool.submit(() -> {
				ready.countDown();
				start.await();
				task.run();
				return null;
			})).toList();
			ready.await();
			start.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@TestConfiguration
	static class LockedJobConfig {

		@Bean
		LockedJob lockedJob() {
			return new LockedJob();
		}
	}

	static class LockedJob {

		// 프록시(CGLIB)를 거치면 필드가 아닌 메서드로 읽어야 실제 객체의 값이 보인다
		private final AtomicInteger executions = new AtomicInteger();

		@SchedulerLock(name = "test.locked-job", lockAtMostFor = "1m")
		public void run() {
			executions.incrementAndGet();
			sleep(TASK_DURATION);
		}

		public int executions() {
			return executions.get();
		}
	}
}
