package com.team7.rental.ai.worker;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** ai.compare 큐 적재 (REQ-040). 기본 exchange + 라우팅 키 = 큐 이름 */
@Component
@RequiredArgsConstructor
public class AiCompareQueue {

	public static final String NAME = "ai.compare";

	private final RabbitTemplate rabbitTemplate;

	/** @throws org.springframework.amqp.AmqpException 브로커 연결·전송 실패 */
	public void publish(Long rentalId) {
		rabbitTemplate.convertAndSend("", NAME, new AiCompareMessage(rentalId));
	}
}
