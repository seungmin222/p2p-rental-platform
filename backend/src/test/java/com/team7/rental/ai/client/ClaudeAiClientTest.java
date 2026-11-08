package com.team7.rental.ai.client;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;

/** 요청 구성만 본다(네트워크 호출 없음). 실제 호출은 ClaudeAiClientLiveTest(@Tag ai-live) */
class ClaudeAiClientTest {

	private final AiProperties properties = new AiProperties("test-key", "claude-haiku-4-5", 1024,
		Duration.ofSeconds(30), 2);
	private final ClaudeAiClient client = new ClaudeAiClient(AnthropicOkHttpClient.builder()
		.apiKey("test-key")
		.build(), properties);

	@Test
	@DisplayName("설정의 모델·시스템 프롬프트로, 인계 사진 → 반납 사진 → 지시 순서의 user 메시지 하나를 만든다")
	void params() {
		// given
		AiImage jpeg = new AiImage("image/jpeg", new byte[] {1, 2, 3});
		AiImage png = new AiImage("image/png", new byte[] {4, 5});

		// when
		MessageCreateParams params = client.params(List.of(jpeg, jpeg), List.of(png));

		// then
		assertThat(params.model().asString()).isEqualTo("claude-haiku-4-5");
		assertThat(params.maxTokens()).isEqualTo(1024);
		assertThat(params.system()).hasValueSatisfying(system -> assertThat(system.asString())
			.contains("조명, 촬영 각도").contains("SCRATCH").contains("MISSING_PART"));
		assertThat(params.messages()).singleElement().satisfies(message -> {
			assertThat(message.role()).isEqualTo(MessageParam.Role.USER);
			List<ContentBlockParam> blocks = message.content().asContentBlockParams();
			// 텍스트, 인계 2장, 텍스트, 반납 1장, 지시
			assertThat(blocks).hasSize(6);
			assertThat(blocks.get(1).isImage()).isTrue();
			assertThat(blocks.get(1).asImage().source().asBase64().data()).isEqualTo("AQID");
			assertThat(blocks.get(4).asImage().source().asBase64().mediaType().asString()).isEqualTo("image/png");
			assertThat(blocks.get(5).asText().text()).contains("앞의 2장은 인계", "뒤의 1장은 반납");
		});
	}

	@Test
	@DisplayName("jpg·png가 아닌 이미지는 보내지 않는다")
	void rejectUnsupportedMediaType() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> client.params(List.of(new AiImage("image/gif", new byte[] {1})), List.of()));
	}
}
