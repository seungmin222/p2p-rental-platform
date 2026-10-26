package com.team7.rental.global.storage;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.JwtProvider;
import com.team7.rental.support.IntegrationTestSupport;

/**
 * multipart 한도를 넘는 요청에 Tomcat이 연결을 끊지 않고 400 JSON을 돌려주는지 실제 HTTP로 확인한다.
 * MockMvc는 Tomcat을 거치지 않아 max-swallow-size 동작을 재현하지 못한다.
 * 한도를 작게(1KB) 줄이고, 초과분(약 3MB)이 Tomcat 기본 swallow 한도(2MB)를 넘게 해 운영의 "15MB 사진 한 장" 상황을 흉내 낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.servlet.multipart.max-file-size=1KB",
	"server.tomcat.max-swallow-size=10MB"
})
class MultipartLimitHttpTest extends IntegrationTestSupport {

	private static final int FILE_SIZE = 3 * 1024 * 1024;
	private static final String BOUNDARY = "test-boundary-7f3a";

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProvider jwtProvider;

	@Test
	@DisplayName("max-file-size를 2MB 넘게 초과해도 연결이 끊기지 않고 400 IMAGE_SIZE_EXCEEDED JSON이 온다")
	void oversizedUploadGetsJsonError() throws Exception {
		// given
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test/upload"))
			.timeout(Duration.ofSeconds(30))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, "USER"))
			.header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + BOUNDARY)
			.POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(new byte[FILE_SIZE])))
			.build();

		// when: 연결이 끊기면 여기서 IOException이 난다
		HttpResponse<String> response;
		try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
		}

		// then
		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).hasValueSatisfying(
			type -> assertThat(type).startsWith(MediaType.APPLICATION_JSON_VALUE));
		assertThat(response.body())
			.contains("\"success\":false")
			.contains("\"code\":\"IMAGE_SIZE_EXCEEDED\"");
	}

	private static byte[] multipartBody(byte[] file) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.writeBytes(("--" + BOUNDARY + "\r\n"
			+ "Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\n"
			+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		body.writeBytes(file);
		body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
		return body.toByteArray();
	}

	@TestConfiguration
	static class UploadControllerConfig {

		@Bean
		UploadController uploadController() {
			return new UploadController();
		}
	}

	@RestController
	static class UploadController {

		@PostMapping(value = "/test/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
		ApiResponse<Void> upload(@RequestPart("image")
		MultipartFile image) {
			return ApiResponse.ok();
		}
	}
}
