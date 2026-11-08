package com.team7.rental.ai.worker;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.team7.rental.ai.service.AiCompareService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ai.compare 큐 소비 (REQ-040). 예외를 밖으로 내지 않아 메시지는 항상 ack된다(실패는 ai_result FAILED로 남는다).
 * test 프로필은 리스너를 자동 시작하지 않는다(application-test.yml). 앞 테스트의 메시지가 다음 테스트 데이터를 건드리지 않게 하려는 것이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCompareListener {

	public static final String ID = "aiCompareListener";

	private final AiCompareService aiCompareService;

	@RabbitListener(id = ID, queues = AiCompareQueue.NAME)
	public void onMessage(AiCompareMessage message) {
		if (message == null || message.rentalId() == null) {
			log.warn("AI compare message without rentalId: {}", message);
			return;
		}
		try {
			aiCompareService.compare(message.rentalId());
		} catch (RuntimeException e) {
			log.error("AI compare worker failed: rentalId={}", message.rentalId(), e);
		}
	}
}
