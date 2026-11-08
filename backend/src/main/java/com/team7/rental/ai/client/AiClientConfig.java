package com.team7.rental.ai.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import lombok.extern.slf4j.Slf4j;

/** 키가 있으면 Claude, 없으면 호출하지 않는 구현. 테스트는 FakeAiClient(@Primary)가 대신한다 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

	@Bean
	public AiClient aiClient(AiProperties properties) {
		if (!properties.hasApiKey()) {
			log.info("ANTHROPIC_API_KEY not set: AI compare disabled (results will be FAILED)");
			return new DisabledAiClient(properties.model());
		}
		return new ClaudeAiClient(AnthropicOkHttpClient.builder()
			.apiKey(properties.apiKey())
			.timeout(properties.timeout())
			.maxRetries(properties.sdkMaxRetries())
			.build(), properties);
	}
}
