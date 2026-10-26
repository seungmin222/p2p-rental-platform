package com.team7.rental.global.error;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.team7.rental.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("성공 응답은 success=true, data, error=null 형식이다")
	void success() throws Exception {
		// when, then
		mockMvc.perform(get("/test/ok"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data").value("hello"))
			.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	@DisplayName("BusinessException은 ErrorCode의 HTTP 상태·코드·메시지로 응답한다")
	void businessException() throws Exception {
		// given
		ErrorCode errorCode = ErrorCode.AUTH_FORBIDDEN;

		// when, then
		mockMvc.perform(get("/test/business"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").value(nullValue()))
			.andExpect(jsonPath("$.error.code").value(errorCode.getCode()))
			.andExpect(jsonPath("$.error.message").value(errorCode.getMessage()))
			.andExpect(jsonPath("$.error.details").isEmpty());
	}

	@Test
	@DisplayName("요청 본문 검증 실패는 400과 필드별 details를 반환한다")
	void bodyValidation() throws Exception {
		// given
		String body = """
			{ "name": " ", "startDate": null }
			""";

		// when, then
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details", hasSize(2)))
			.andExpect(jsonPath("$.error.details[*].field", containsInAnyOrder("name", "startDate")))
			.andExpect(jsonPath("$.error.details[?(@.field == 'name')].reason").value("이름은 필수입니다."));
	}

	@Test
	@DisplayName("요청 파라미터 제약 위반은 400과 파라미터 이름의 details를 반환한다")
	void parameterValidation() throws Exception {
		// when, then
		mockMvc.perform(get("/test/param").param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("size"))
			.andExpect(jsonPath("$.error.details[0].reason").value("100 이하여야 합니다."));
	}

	@Test
	@DisplayName("파라미터 타입이 맞지 않으면 400과 해당 필드를 반환한다")
	void typeMismatch() throws Exception {
		// when, then
		mockMvc.perform(get("/test/param").param("size", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("size"));
	}

	@Test
	@DisplayName("필수 파라미터가 없으면 400과 해당 필드를 반환한다")
	void missingParameter() throws Exception {
		// when, then
		mockMvc.perform(get("/test/param"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("size"));
	}

	@Test
	@DisplayName("본문 필드 형식이 틀리면 400과 해당 필드 경로를 반환한다")
	void invalidFieldFormat() throws Exception {
		// given
		String body = """
			{ "name": "drill", "startDate": "2026/10/03" }
			""";

		// when, then
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("startDate"));
	}

	@Test
	@DisplayName("JSON 문법 오류는 400을 반환한다")
	void malformedJson() throws Exception {
		// when, then
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{ \"name\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("지원하지 않는 HTTP 메서드는 405를 반환한다")
	void methodNotAllowed() throws Exception {
		// when, then
		mockMvc.perform(post("/test/ok"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.error.code").value("COMMON_METHOD_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("없는 경로는 404를 반환한다")
	void notFound() throws Exception {
		// when, then
		mockMvc.perform(get("/test/unknown"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("COMMON_NOT_FOUND"));
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500과 고정 메시지를 반환하고 내부 메시지를 노출하지 않는다")
	void unexpectedException() throws Exception {
		// when, then
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error.code").value("COMMON_INTERNAL_ERROR"))
			.andExpect(jsonPath("$.error.message").value(ErrorCode.COMMON_INTERNAL_ERROR.getMessage()))
			.andExpect(content().string(not(containsString("secret-detail"))));
	}

	@Test
	@DisplayName("multipart 한도 초과는 500이 아니라 400 IMAGE_SIZE_EXCEEDED다")
	void maxUploadSizeExceeded() throws Exception {
		// when, then
		mockMvc.perform(post("/test/upload"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_SIZE_EXCEEDED"));
	}

	@RestController
	static class TestController {

		@PostMapping("/test/upload")
		ApiResponse<Void> upload() {
			throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
		}

		@GetMapping("/test/ok")
		ApiResponse<String> ok() {
			return ApiResponse.ok("hello");
		}

		@GetMapping("/test/business")
		ApiResponse<Void> business() {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}

		@PostMapping("/test/body")
		ApiResponse<Void> body(@Valid @RequestBody
		TestRequest request) {
			return ApiResponse.ok();
		}

		@GetMapping("/test/param")
		ApiResponse<Integer> param(@RequestParam @Max(value = 100, message = "100 이하여야 합니다.")
		Integer size) {
			return ApiResponse.ok(size);
		}

		@GetMapping("/test/unexpected")
		ApiResponse<Void> unexpected() {
			throw new IllegalStateException("secret-detail");
		}
	}

	record TestRequest(
		@NotBlank(message = "이름은 필수입니다.")
		String name,
		@NotNull(message = "시작일은 필수입니다.")
		LocalDate startDate) {
	}
}
