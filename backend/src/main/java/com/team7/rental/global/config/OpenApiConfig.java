package com.team7.rental.global.config;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.error.ErrorCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Swagger(springdoc) 설정. 인증이 필요한 API는 {@code @SecurityRequirement(name = "bearerAuth")}를 붙인다.
 * 공개 범위는 SecurityConfig(app.security.permit-swagger)와 application-prod.yml(springdoc 비활성)에서 정한다.
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Rental Platform API")
				.version("v1")
				.description("자동 생성 파일. 직접 수정 금지. backend에서 ./gradlew generateOpenApiDocs 로 재생성."))
			// 같은 출처로 호출한다(로컬 Vite 프록시, 운영 Nginx). 생성 파일에 localhost가 박히지 않게 상대 경로로 둔다
			.addServersItem(new Server().url("/"))
			.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.description("Access Token. 로그인 응답의 토큰을 그대로 넣는다(Bearer 접두사 제외).")));
	}

	/** @ApiErrorCodes에 선언한 에러 코드를 HTTP 상태별 응답 예시로 추가한다. */
	@Bean
	public OperationCustomizer apiErrorCodesCustomizer() {
		return (operation, handlerMethod) -> {
			ApiErrorCodes annotation = handlerMethod.getMethodAnnotation(ApiErrorCodes.class);
			if (annotation == null) {
				return operation;
			}
			if (operation.getResponses() == null) {
				operation.setResponses(new ApiResponses());
			}
			Map<Integer, Map<String, Example>> examplesByStatus = new TreeMap<>();
			Arrays.stream(annotation.value()).forEach(errorCode -> examplesByStatus
				.computeIfAbsent(errorCode.getStatus().value(), status -> new TreeMap<>())
				.put(errorCode.getCode(), toExample(errorCode)));
			examplesByStatus.forEach((status, examples) -> operation.getResponses()
				.addApiResponse(String.valueOf(status), new ApiResponse()
					.description(String.join(", ", examples.keySet()))
					.content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
						new io.swagger.v3.oas.models.media.MediaType()
							.examples(examples)))));
			return operation;
		};
	}

	private Example toExample(ErrorCode errorCode) {
		return new Example()
			.summary(errorCode.getMessage())
			.value(com.team7.rental.global.response.ApiResponse.fail(errorCode));
	}
}
