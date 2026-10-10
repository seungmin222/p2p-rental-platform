package com.team7.rental.global.config;

import static com.team7.rental.global.error.ErrorCode.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTest {

	private final OpenApiConfig config = new OpenApiConfig();
	private final OperationCustomizer customizer = config.apiErrorCodesCustomizer();

	@Test
	@DisplayName("@ApiErrorCodes의 코드가 HTTP 상태별 응답 예시로 추가된다")
	void addsErrorExamplesByStatus() throws Exception {
		// given
		HandlerMethod handlerMethod = handlerMethod("withErrors");

		// when
		Operation operation = customizer.customize(new Operation(), handlerMethod);

		// then
		assertThat(operation.getResponses()).containsOnlyKeys("400", "401", "403");
		Example example = operation.getResponses().get("403").getContent().get("application/json")
			.getExamples().get("AUTH_FORBIDDEN");
		assertThat(example.getSummary()).isEqualTo(AUTH_FORBIDDEN.getMessage());
		assertThat(example.getValue()).isEqualTo(ApiResponse.fail(AUTH_FORBIDDEN));
		assertThat(operation.getResponses().get("400").getDescription()).isEqualTo("COMMON_INVALID_INPUT");
	}

	@Test
	@DisplayName("@ApiErrorCodes가 없으면 응답을 건드리지 않는다")
	void ignoresMethodWithoutAnnotation() throws Exception {
		// when
		Operation operation = customizer.customize(new Operation(), handlerMethod("withoutErrors"));

		// then
		assertThat(operation.getResponses()).isNull();
	}

	@Test
	@DisplayName("JWT bearerAuth 보안 스키마와 상대 경로 서버를 등록한다")
	void registersBearerAuthAndRelativeServer() {
		// when
		OpenAPI openApi = config.openApi();

		// then
		SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);
		assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
		assertThat(scheme.getScheme()).isEqualTo("bearer");
		assertThat(openApi.getServers()).extracting("url").containsExactly("/");
	}

	private HandlerMethod handlerMethod(String name) throws NoSuchMethodException {
		return new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod(name));
	}

	static class SampleController {

		@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, AUTH_FORBIDDEN})
		void withErrors() {}

		void withoutErrors() {}
	}
}
