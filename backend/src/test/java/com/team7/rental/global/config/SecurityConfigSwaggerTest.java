package com.team7.rental.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.global.security.SecurityErrorResponder;

// 실제 컨트롤러를 올리지 않는다(서비스 빈이 이 슬라이스에 없다). 빈 컨트롤러만 대상으로 지정한다
@WebMvcTest(controllers = SecurityConfigSwaggerTest.NoController.class, properties = {
	"app.jwt.secret=test-jwt-secret-must-be-at-least-32-bytes-long",
	"app.security.permit-swagger=true"})
@Import({SecurityConfig.class, JwtProvider.class, SecurityErrorResponder.class, TimeConfig.class})
class SecurityConfigSwaggerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("Swagger 공개 설정(local 프로필)이면 토큰 없이 Swagger 경로에 접근할 수 있다")
	void swaggerPublicWhenEnabled() throws Exception {
		// springdoc은 이 슬라이스에 올라오지 않으므로 인증을 통과해 404가 되는 것으로 확인한다
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

	@RestController
	static class NoController {}
}
