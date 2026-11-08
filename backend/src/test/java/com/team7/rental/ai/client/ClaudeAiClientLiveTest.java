package com.team7.rental.ai.client;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.team7.rental.ai.service.AiCompareParser;
import com.team7.rental.global.storage.ImageFormat;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Claude 호출 (REQ-040 검증 실험, docs/experiments/ai-compare.md). 기본 test 작업에서 제외된다(@Tag ai-live, 과금됨).
 * <pre>
 * ANTHROPIC_API_KEY=... AI_COMPARE_SAMPLE_DIR=... ./gradlew test -PaiLive --tests '*ClaudeAiClientLiveTest'
 * </pre>
 * 샘플 폴더 구조: {@code <쌍 이름>/handover-*.jpg|png}, {@code <쌍 이름>/return-*.jpg|png}. 쌍마다 스키마 준수 여부·소요 시간·결과를 출력한다.
 */
@Tag("ai-live")
class ClaudeAiClientLiveTest {

	@Test
	@DisplayName("샘플 쌍을 실제로 비교해 스키마 준수·소요 시간을 출력한다")
	void compareSamples() throws IOException {
		String apiKey = System.getenv("ANTHROPIC_API_KEY");
		String sampleDir = System.getenv("AI_COMPARE_SAMPLE_DIR");
		assumeTrue(apiKey != null && !apiKey.isBlank(), "ANTHROPIC_API_KEY 없음");
		assumeTrue(sampleDir != null && Files.isDirectory(Path.of(sampleDir)), "AI_COMPARE_SAMPLE_DIR 없음");

		AiProperties properties = new AiProperties(apiKey, configuredModel(), 1024, Duration.ofSeconds(30), 2);
		ClaudeAiClient client = new ClaudeAiClient(AnthropicOkHttpClient.builder()
			.apiKey(apiKey)
			.timeout(properties.timeout())
			.maxRetries(properties.sdkMaxRetries())
			.build(), properties);
		AiCompareParser parser = new AiCompareParser(JsonMapper.builder().build());

		int pairs = 0;
		int valid = 0;
		try (Stream<Path> dirs = Files.list(Path.of(sampleDir))) {
			for (Path pair : dirs.filter(Files::isDirectory).sorted().toList()) {
				List<AiImage> handover = images(pair, "handover-");
				List<AiImage> returned = images(pair, "return-");
				long started = System.nanoTime();
				String text = client.compare(handover, returned);
				long millis = (System.nanoTime() - started) / 1_000_000;
				var result = parser.parse(text, returned.size());
				pairs++;
				valid += result.isPresent() ? 1 : 0;
				System.out.printf("%s\t%dms\tvalid=%s\t%s%n", pair.getFileName(), millis, result.isPresent(),
					result.map(Object::toString).orElse(text));
			}
		}
		System.out.printf("schema valid %d/%d%n", valid, pairs);
		assertThat(pairs).isPositive();
	}

	/** 운영과 같은 모델로 실험하도록 application.yml의 app.ai.model을 읽는다 */
	private static String configuredModel() {
		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		yaml.setResources(new ClassPathResource("application.yml"));
		String model = yaml.getObject().getProperty("app.ai.model");
		assertThat(model).as("application.yml app.ai.model").isNotBlank();
		return model;
	}

	private static List<AiImage> images(Path pair, String prefix) throws IOException {
		try (Stream<Path> files = Files.list(pair)) {
			return files.filter(file -> file.getFileName().toString().startsWith(prefix))
				.sorted()
				.map(ClaudeAiClientLiveTest::read)
				.toList();
		}
	}

	private static AiImage read(Path file) {
		try {
			byte[] data = Files.readAllBytes(file);
			ImageFormat format = ImageFormat.detect(data).orElseThrow();
			return new AiImage(format.getContentType(), data);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
