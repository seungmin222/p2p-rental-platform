package com.team7.rental.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 저장 AFTER_COMMIT 리스너의 본문을 요청 스레드 밖에서 돌리는 전용 실행기.
 * <p>
 * AFTER_COMMIT 콜백은 커밋 직후·커넥션 반납 전에 같은 스레드에서 돈다. 여기서 알림을 저장하면(REQUIRES_NEW) 요청 하나가 커넥션 2개를 잡아,
 * 동시 요청이 풀 크기를 넘으면 Hikari 풀이 교착된다(docs/experiments/load-test.md "병목"). 이 실행기에 맡기면 요청 스레드는 커넥션·물품 락을
 * 바로 돌려주고, 실행기 스레드는 커넥션을 하나씩만 잡는다.
 * <ul>
 *   <li>큐가 가득 차거나 종료 중이면 버리고 경고 로그만 남긴다. 호출자 스레드 실행(CallerRuns)은 과부하 때 위 교착을 다시 만들므로 쓰지 않는다.</li>
 *   <li>종료 때는 웹 서버가 멈춘 뒤 대기 중인 작업을 정해진 시간까지 기다린다. SmartLifecycle이라 DataSource가 닫히기 전에 멈춘다.</li>
 *   <li>맡긴 작업의 예외는 잡아 로그만 남긴다.</li>
 * </ul>
 * 실행기를 빈으로 내놓지 않는 것은 Executor 빈이 생기면 Boot 기본 applicationTaskExecutor가 빠지기 때문이다(PasswordResetMailDispatcher와 같다).
 * 테스트는 app.notification.executor.async=false로 호출 스레드에서 바로 돌려 결과를 결정적으로 검증한다.
 */
@Slf4j
@Component
public class NotificationListenerExecutor implements SmartLifecycle {

	/** 웹 서버(graceful shutdown 포함)보다 늦게 멈춰, 마지막 요청이 남긴 알림까지 받는다 */
	private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 4096;

	private final TaskExecutor executor;
	private volatile boolean running;

	public NotificationListenerExecutor(@Value("${app.notification.executor.async:true}")
	boolean async, @Value("${app.notification.executor.pool-size:4}")
	int poolSize, @Value("${app.notification.executor.queue-capacity:1000}")
	int queueCapacity, @Value("${app.notification.executor.await-termination-seconds:10}")
	int awaitTerminationSeconds) {
		if (async) {
			ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
			pool.setThreadNamePrefix("notify-");
			pool.setCorePoolSize(poolSize);
			pool.setMaxPoolSize(poolSize);
			pool.setQueueCapacity(queueCapacity);
			pool.setWaitForTasksToCompleteOnShutdown(true);
			pool.setAwaitTerminationSeconds(awaitTerminationSeconds);
			pool.initialize();
			this.executor = pool;
		} else {
			this.executor = new SyncTaskExecutor();
		}
	}

	/** 호출 스레드에서 바로 실행하는 실행기 (단위 테스트용) */
	public static NotificationListenerExecutor synchronous() {
		return new NotificationListenerExecutor(false, 1, 0, 0);
	}

	/**
	 * 작업을 맡긴다. 거절되거나 작업이 실패해도 던지지 않는다.
	 *
	 * @param description 실패 로그에 남길 식별 정보(회원 id·종류·event id 등)
	 */
	public void execute(String description, Runnable task) {
		try {
			executor.execute(() -> {
				try {
					task.run();
				} catch (RuntimeException e) {
					log.warn("Notification listener task failed: {}, error={}", description, e.toString());
				}
			});
		} catch (RuntimeException e) {
			log.warn("Notification listener task dropped (queue full or shutting down): {}, error={}", description,
				e.toString());
		}
	}

	@Override
	public void start() {
		running = true;
	}

	@Override
	public void stop() {
		running = false;
		if (executor instanceof ThreadPoolTaskExecutor pool) {
			pool.shutdown();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public int getPhase() {
		return PHASE;
	}
}
