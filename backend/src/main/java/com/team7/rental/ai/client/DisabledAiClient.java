package com.team7.rental.ai.client;

import java.util.List;

/** ANTHROPIC_API_KEY가 없을 때(로컬 기본). 호출하지 않고 실패로 끝내 결과는 FAILED, 거래 흐름은 그대로다 */
public class DisabledAiClient implements AiClient {

	private final String model;

	public DisabledAiClient(String model) {
		this.model = model;
	}

	@Override
	public String compare(List<AiImage> handoverImages, List<AiImage> returnImages) {
		throw new AiClientException("ANTHROPIC_API_KEY가 없어 AI 비교를 하지 않는다");
	}

	@Override
	public String model() {
		return model;
	}
}
