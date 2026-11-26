package com.team7.rental.global.config;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationListenerExecutorTest {

	private NotificationListenerExecutor executor;

	@AfterEach
	void tearDown() {
		if (executor != null) {
			executor.stop();
		}
	}

	@Test
	@DisplayName("작업은 호출 스레드가 아닌 notify- 스레드에서 돈다")
	void runsOnDedicatedThread() throws InterruptedException {
		// given
		executor = new NotificationListenerExecutor(true, 2, 10, 1);
		AtomicReference<String> threadName = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);

		// when
		executor.execute("test", () -> {
			threadName.set(Thread.currentThread().getName());
			done.countDown();
		});

		// then
		assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(threadName.get()).startsWith("notify-");
	}

	@Test
	@DisplayName("큐가 가득 차면 넘친 작업은 버리고 던지지 않는다")
	void dropsWhenQueueFull() throws InterruptedException {
		// given: 스레드 1개가 막혀 있고 큐 1칸이 찬 상태
		executor = new NotificationListenerExecutor(true, 1, 1, 1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger ran = new AtomicInteger();
		Runnable blocking = () -> {
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			ran.incrementAndGet();
		};
		executor.execute("running", blocking);
		executor.execute("queued", ran::incrementAndGet);

		// when & then
		assertThatCode(() -> executor.execute("overflow", ran::incrementAndGet)).doesNotThrowAnyException();
		release.countDown();
		executor.stop();
		assertThat(ran.get()).isEqualTo(2);
	}

	@Test
	@DisplayName("작업이 던진 예외는 밖으로 나가지 않는다")
	void swallowsTaskFailure() {
		// given
		executor = NotificationListenerExecutor.synchronous();

		// when & then
		assertThatCode(() -> executor.execute("boom", () -> {
			throw new IllegalStateException("boom");
		})).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("멈출 때 대기 중인 작업을 마치고, 멈춘 뒤 맡긴 작업은 버린다")
	void drainsOnStopThenDrops() {
		// given
		executor = new NotificationListenerExecutor(true, 1, 10, 5);
		AtomicInteger ran = new AtomicInteger();
		for (int i = 0; i < 5; i++) {
			executor.execute("task" + i, () -> {
				sleep(20);
				ran.incrementAndGet();
			});
		}

		// when
		executor.stop();

		// then
		assertThat(ran.get()).isEqualTo(5);
		assertThatCode(() -> executor.execute("late", ran::incrementAndGet)).doesNotThrowAnyException();
		assertThat(ran.get()).isEqualTo(5);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
