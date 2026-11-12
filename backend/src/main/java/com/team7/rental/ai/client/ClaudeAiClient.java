package com.team7.rental.ai.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;

import lombok.extern.slf4j.Slf4j;

/**
 * Claude Messages API (anthropic-java). 이미지는 base64로 보낸다: 로컬 디스크 저장소의 URL은 Anthropic이 열 수 없어서이고, 운영 S3도 같은
 * 방식을 써 프로필마다 동작이 달라지지 않게 한다. 응답·사진 내용은 로그에 남기지 않는다.
 */
@Slf4j
public class ClaudeAiClient implements AiClient {

	private final AnthropicClient client;
	/** 카테고리 추천용: 사용자가 기다리는 요청이라 SDK 재시도를 끄고 제한 시간은 호출마다 준다 */
	private final AnthropicClient noRetryClient;
	private final AiProperties properties;

	public ClaudeAiClient(AnthropicClient client, AiProperties properties) {
		this.client = client;
		this.noRetryClient = client.withOptions(options -> options.maxRetries(0));
		this.properties = properties;
	}

	@Override
	public String compare(List<AiImage> handoverImages, List<AiImage> returnImages) {
		Message message;
		try {
			message = client.messages().create(params(handoverImages, returnImages));
		} catch (AnthropicException e) {
			throw new AiClientException("Claude 호출 실패: " + e.getClass().getSimpleName(), e);
		}
		return textOf(message, "compare");
	}

	@Override
	public String recommendCategory(List<AiImage> images, List<CategoryOption> categories, Duration timeout) {
		List<ContentBlockParam> blocks = new ArrayList<>();
		images.forEach(image -> blocks.add(image(image)));
		blocks.add(text(CategoryPrompt.instruction(images.size(), categories)));
		MessageCreateParams params = MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(properties.maxTokens())
			.system(CategoryPrompt.SYSTEM)
			.addUserMessageOfContentBlockParams(blocks)
			.build();
		Message message;
		try {
			message = noRetryClient.messages().create(params, RequestOptions.builder().timeout(timeout).build());
		} catch (AnthropicException e) {
			throw new AiClientException("Claude 호출 실패: " + e.getClass().getSimpleName(), e);
		}
		return textOf(message, "category");
	}

	private String textOf(Message message, String purpose) {
		StopReason stopReason = message.stopReason().orElse(null);
		log.info("Claude {}: model={}, stopReason={}, inputTokens={}, outputTokens={}", purpose, properties.model(),
			stopReason, message.usage().inputTokens(), message.usage().outputTokens());
		if (StopReason.REFUSAL.equals(stopReason)) {
			throw new AiClientException("Claude가 요청을 거절했다");
		}
		return message.content().stream()
			.flatMap(block -> block.text().stream())
			.map(TextBlock::text)
			.collect(Collectors.joining());
	}

	@Override
	public String model() {
		return properties.model();
	}

	MessageCreateParams params(List<AiImage> handoverImages, List<AiImage> returnImages) {
		return MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(properties.maxTokens())
			.system(ComparePrompt.SYSTEM)
			.addUserMessageOfContentBlockParams(content(handoverImages, returnImages))
			.build();
	}

	private static List<ContentBlockParam> content(List<AiImage> handoverImages, List<AiImage> returnImages) {
		List<ContentBlockParam> blocks = new ArrayList<>();
		blocks.add(text("인계 사진 " + handoverImages.size() + "장:"));
		handoverImages.forEach(image -> blocks.add(image(image)));
		blocks.add(text("반납 사진 " + returnImages.size() + "장:"));
		returnImages.forEach(image -> blocks.add(image(image)));
		blocks.add(text(ComparePrompt.instruction(handoverImages.size(), returnImages.size())));
		return blocks;
	}

	private static ContentBlockParam text(String text) {
		return ContentBlockParam.ofText(TextBlockParam.builder().text(text).build());
	}

	private static ContentBlockParam image(AiImage image) {
		Base64ImageSource.MediaType mediaType = switch (image.mediaType()) {
			case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
			case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
			default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식: " + image.mediaType());
		};
		return ContentBlockParam.ofImage(ImageBlockParam.builder()
			.source(Base64ImageSource.builder()
				.mediaType(mediaType)
				.data(Base64.getEncoder().encodeToString(image.data()))
				.build())
			.build());
	}
}
