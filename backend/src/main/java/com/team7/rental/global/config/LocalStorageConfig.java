package com.team7.rental.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.team7.rental.global.storage.LocalFileStorage;
import com.team7.rental.global.storage.LocalStorageProperties;

/** local·test 프로필: 디스크에 저장하고 {urlPrefix}/**로 서빙한다. 운영(prod)은 S3StorageConfig. */
@Configuration
@Profile({"local", "test"})
@EnableConfigurationProperties(LocalStorageProperties.class)
public class LocalStorageConfig implements WebMvcConfigurer {

	private final LocalStorageProperties properties;

	public LocalStorageConfig(LocalStorageProperties properties) {
		this.properties = properties;
	}

	@Bean
	public LocalFileStorage localFileStorage() {
		return new LocalFileStorage(properties);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = properties.rootDir().toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler(properties.urlPrefix() + "/**")
			.addResourceLocations(location.endsWith("/") ? location : location + "/");
	}
}
