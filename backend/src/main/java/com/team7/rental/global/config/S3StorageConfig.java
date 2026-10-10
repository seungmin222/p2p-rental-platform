package com.team7.rental.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.team7.rental.global.storage.S3FileStorage;
import com.team7.rental.global.storage.S3StorageProperties;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** prod 프로필에서만 S3Client를 만든다. local·test는 AWS 키 없이 LocalStorageConfig를 쓴다. */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

	@Bean(destroyMethod = "close")
	public S3Client s3Client(S3StorageProperties properties) {
		return S3Client.builder()
			.region(Region.of(properties.region()))
			.build();
	}

	@Bean
	public S3FileStorage s3FileStorage(S3Client s3Client, S3StorageProperties properties) {
		return new S3FileStorage(s3Client, properties);
	}
}
