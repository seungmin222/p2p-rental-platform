package com.team7.rental.payment.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 토스페이먼츠 연동 설정. 정책 값이 아니라 기술 설정이다(근거는 docs/prompts/feat/payment-core/notes.md).
 *
 * @param secretKey API 개별 연동 시크릿 키. 결제를 켰는데 비어 있으면 기동 실패
 */
@Validated
@ConfigurationProperties("app.payment.toss")
public record TossPaymentProperties(
	@NotBlank
	String baseUrl,
	String secretKey,
	@NotNull
	Duration connectTimeout,
	@NotNull
	Duration readTimeout) {
}
