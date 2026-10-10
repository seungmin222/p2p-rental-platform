package com.team7.rental.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * @param publicUrl 업로드한 객체를 브라우저가 읽는 기본 주소(끝에 / 없이). 기본값은 버킷 주소, CDN을 붙이면 그 주소
 */
@Validated
@ConfigurationProperties("app.storage.s3")
public record S3StorageProperties(
	@NotBlank
	String region,
	@NotBlank
	String bucket,
	@NotBlank
	String publicUrl) {
}
