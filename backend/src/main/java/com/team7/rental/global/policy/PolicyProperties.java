package com.team7.rental.global.policy;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * docs/policies.md의 정책 수치. 값은 application.yml app.policy.* 에 두고, 바꾸면 policies.md도 함께 고친다.
 * 도메인 정책은 각 도메인 작업에서 섹션(record)을 추가한다.
 */
@Validated
@ConfigurationProperties("app.policy")
public record PolicyProperties(
	@Valid @NotNull
	Image image,
	@Valid @NotNull
	Region region,
	@Valid @NotNull
	Trust trust,
	@Valid @NotNull
	Rental rental,
	@Valid @NotNull
	Payment payment) {

	/**
	 * @param originalMaxLongSide 원본 재인코딩 시 긴 변 최대 픽셀 (image.original-max-long-side)
	 * @param thumbnailWidth 썸네일 가로 픽셀 (thumbnail.width)
	 * @param maxPixels 업로드 이미지 최대 픽셀 수(가로 × 세로) (image.max-pixels)
	 */
	public record Image(
		@Positive
		int originalMaxLongSide,
		@Positive
		int thumbnailWidth,
		@Positive
		long maxPixels) {
	}

	/**
	 * @param verifyValidDays 동네 인증 유효 일수 (region.verify-valid-days)
	 */
	public record Region(
		@Positive
		int verifyValidDays) {
	}

	/**
	 * @param initialScore 가입 시 신뢰 점수 (trust.initial)
	 */
	public record Trust(
		@NotNull
		BigDecimal initialScore) {
	}

	/**
	 * @param minDays 최초 예약 최소 일수 (rental.min-days)
	 * @param maxDays 최초 예약 최대 일수 (rental.max-days)
	 * @param lockWait 분산 락 대기 시간 (rental.lock-wait). 임대 시간은 두지 않는다(Redisson watchdog)
	 */
	public record Rental(
		@Positive
		int minDays,
		@Positive
		int maxDays,
		@NotNull
		Duration lockWait) {
	}

	/**
	 * @param expireMinutes 결제 대기 만료 분 (payment.expire-minutes)
	 */
	public record Payment(
		@Positive
		int expireMinutes) {
	}
}
