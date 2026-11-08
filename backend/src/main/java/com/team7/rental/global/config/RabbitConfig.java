package com.team7.rental.global.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.team7.rental.ai.worker.AiCompareQueue;

import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ AMQP 작업 큐 (docs/architecture.md). 기본 exchange로 큐 이름에 바로 보낸다. 메시지는 JSON.
 * 리스너가 예외를 던져도 다시 큐에 넣지 않는다(spring.rabbitmq.listener.simple.default-requeue-rejected=false): 같은 메시지가 끝없이 돌지 않게.
 */
@Configuration
public class RabbitConfig {

	/** AI 상태 비교 큐 (REQ-040). 메시지 {@code {"rentalId": 1}} */
	@Bean
	public Queue aiCompareAmqpQueue() {
		return QueueBuilder.durable(AiCompareQueue.NAME).build();
	}

	/** RabbitTemplate·리스너 컨테이너가 같이 쓴다(Boot 자동 설정) */
	@Bean
	public MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
		return new JacksonJsonMessageConverter(jsonMapper, "com.team7.rental.ai.worker");
	}
}
