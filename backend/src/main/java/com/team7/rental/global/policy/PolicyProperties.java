package com.team7.rental.global.policy;

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
	Image image) {

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
}
