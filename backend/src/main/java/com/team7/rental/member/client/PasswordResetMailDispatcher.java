package com.team7.rental.member.client;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 재설정 메일을 백그라운드에서 보낸다 (REQ-036). 응답을 메일 발송과 떼어 가입 여부와 상관없이 같은 응답·응답 시간을 준다. 실패는 로그만 남기고(이메일 주소는 남기지
 * 않는다) 사용자는 요청 제한 시간 뒤 다시 요청한다. 실행기를 빈으로 내놓지 않는 것은 Executor 빈이 생기면 Boot 기본 applicationTaskExecutor가 빠지기 때문이다.
 * 테스트는 app.mail.async=false로 호출 스레드에서 바로 보내 결과를 결정적으로 검증한다.
 */
@Slf4j
@Component
public class PasswordResetMailDispatcher implements DisposableBean {

	private final TaskExecutor executor;

	public PasswordResetMailDispatcher(@Value("${app.mail.async:true}")
	boolean async) {
		if (async) {
			ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
			pool.setThreadNamePrefix("mail-");
			pool.setCorePoolSize(1);
			pool.setMaxPoolSize(2);
			pool.setQueueCapacity(100);
			pool.setWaitForTasksToCompleteOnShutdown(true);
			pool.setAwaitTerminationSeconds(10);
			pool.initialize();
			this.executor = pool;
		} else {
			this.executor = new SyncTaskExecutor();
		}
	}

	/** 발송 작업을 맡긴다. 큐가 가득 차 거절되거나 발송이 실패해도 호출한 요청은 성공으로 끝난다 */
	public void dispatch(Runnable send) {
		try {
			executor.execute(() -> {
				try {
					send.run();
				} catch (RuntimeException e) {
					log.warn("비밀번호 재설정 메일 발송 실패: {}", e.getClass().getSimpleName());
				}
			});
		} catch (RuntimeException e) {
			log.warn("비밀번호 재설정 메일 작업을 맡기지 못했습니다: {}", e.getClass().getSimpleName());
		}
	}

	@Override
	public void destroy() {
		if (executor instanceof ThreadPoolTaskExecutor pool) {
			pool.shutdown();
		}
	}
}
