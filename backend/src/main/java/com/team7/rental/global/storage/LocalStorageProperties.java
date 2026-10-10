package com.team7.rental.global.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @param rootDir 저장 디렉터리 (local: backend/에서 실행 기준 ./uploads)
 * @param urlPrefix 서빙 경로. SecurityConfig의 공개 경로(/files/**)와 맞춘다
 */
@Validated
@ConfigurationProperties("app.storage.local")
public record LocalStorageProperties(
	@NotNull
	Path rootDir,
	@NotNull @Pattern(regexp = "/[a-z0-9-]+")
	String urlPrefix) {
}
