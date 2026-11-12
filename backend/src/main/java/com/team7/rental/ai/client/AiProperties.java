package com.team7.rental.ai.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Claude API 연결 설정 (app.ai, REQ-040). 정책 수치(월 한도·재시도)는 PolicyProperties.ai에 있다.
 *
 * @param apiKey ANTHROPIC_API_KEY. 비어 있으면 {@link DisabledAiClient}를 쓴다
 * @param model docs/domains/ai.md의 모델
 * @param maxTokens 응답 최대 토큰. 고정 스키마 JSON 하나라 작다
 * @param timeout 요청 한 번의 제한 시간
 * @param sdkMaxRetries SDK 재시도(429·5xx·연결 오류). 응답 형식 오류 재시도(ai.max-retry)와 별개
 */
@Validated
@ConfigurationProperties("app.ai")
public record AiProperties(
	String apiKey,
	@NotBlank
	String model,
	@Positive
	long maxTokens,
	@NotNull
	Duration timeout,
	@PositiveOrZero
	int sdkMaxRetries) {

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
