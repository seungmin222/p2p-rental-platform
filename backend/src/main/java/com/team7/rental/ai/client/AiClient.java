package com.team7.rental.ai.client;

import java.time.Duration;
import java.util.List;

/**
 * 외부 AI 호출 (REQ-040 상태 비교, REQ-067 카테고리 추천). 운영은 {@link ClaudeAiClient}, 키가 없으면 {@link DisabledAiClient}, 테스트는 FakeAiClient.
 * 트랜잭션 안에서 부르지 않는다(backend/CLAUDE.md).
 */
public interface AiClient {

	/**
	 * 인계 사진과 반납 사진을 비교해 고정 스키마 JSON 텍스트를 받는다. 형식 검증은 부르는 쪽이 한다.
	 *
	 * @param handoverImages 인계 사진 (올린 순서)
	 * @param returnImages 반납 사진 (올린 순서, regions.imageIndex 기준)
	 * @return 모델이 낸 텍스트 그대로
	 * @throws AiClientException 호출 실패
	 */
	String compare(List<AiImage> handoverImages, List<AiImage> returnImages);

	/**
	 * 물품 사진을 보고 주어진 카테고리 중 하나를 고른 고정 스키마 JSON 텍스트를 받는다. 형식 검증은 부르는 쪽이 한다.
	 *
	 * @param images 물품 사진 (올린 순서)
	 * @param categories 고를 수 있는 활성 카테고리
	 * @param timeout 이 호출의 제한 시간 (SDK 재시도 없이 한 번)
	 * @return 모델이 낸 텍스트 그대로
	 * @throws AiClientException 호출 실패·시간 초과
	 */
	String recommendCategory(List<AiImage> images, List<CategoryOption> categories, Duration timeout);

	/** 호출하는 모델 이름 (결과 기록용) */
	String model();
}
