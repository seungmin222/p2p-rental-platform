package com.team7.rental.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.team7.rental.global.security.JwtAuthenticationFilter;
import com.team7.rental.global.security.JwtProperties;
import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.global.security.SecurityErrorResponder;

/**
 * 무상태 JWT 인증. 세션·CSRF·폼 로그인·HTTP Basic을 쓰지 않는다.
 * CORS는 설정하지 않아 기본 차단된다(같은 출처로 동작, docs/architecture.md §4).
 * 경로별 인증은 docs/api.md의 "인증" 열 기준. 새 공개(X·옵션) API를 만들면 아래 목록에 추가한다.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	/** 회원 전용인데 아래 공개 패턴과 겹치는 경로. 공개 규칙보다 먼저 검사한다. */
	private static final String[] MEMBER_ONLY_OVERLAPPING = {
		"/api/members/me", "/api/members/me/**",
		"/api/items/deposit-guide", "/api/items/*/rentals"
	};

	/** 비회원도 조회 가능한 GET API (api.md 인증 X·옵션). 1·2순위, 미구현 API 포함 */
	private static final String[] PUBLIC_GET = {
		// 1순위
		"/api/regions", "/api/regions/by-coord",
		"/api/categories",
		"/api/items", "/api/items/feed", "/api/items/*", "/api/items/*/calendar",
		// 2순위
		"/api/members/*", "/api/members/*/reviews", "/api/members/*/items",
		"/api/terms",
		// 업로드 이미지 (local·test 프로필의 LocalFileStorage 서빙 경로. 운영은 S3 URL)
		"/files/**"
	};

	private static final String[] SWAGGER = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
		SecurityErrorResponder errorResponder,
		@Value("${app.security.permit-swagger:false}")
		boolean permitSwagger) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(errorResponder)
				.accessDeniedHandler(errorResponder))
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider, errorResponder),
				UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests(auth -> {
				auth.requestMatchers("/error").permitAll();
				if (permitSwagger) {
					auth.requestMatchers(SWAGGER).permitAll();
				}
				// /api/auth/** 는 로그아웃 포함 전부 공개 (만료된 AT로도 재발급·로그아웃 가능해야 함)
				auth.requestMatchers(MEMBER_ONLY_OVERLAPPING).authenticated()
					.requestMatchers("/api/admin/**").hasRole("ADMIN")
					.requestMatchers("/api/auth/**").permitAll()
					.requestMatchers(HttpMethod.POST, "/api/members").permitAll()
					.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
					.anyRequest().authenticated();
			});
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
