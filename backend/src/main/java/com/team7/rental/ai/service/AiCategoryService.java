package com.team7.rental.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.ai.client.AiClient;
import com.team7.rental.ai.client.AiImage;
import com.team7.rental.ai.client.CategoryOption;
import com.team7.rental.ai.dto.AiCategoryResponse;
import com.team7.rental.ai.dto.AiCategoryStatus;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ScaledImage;
import com.team7.rental.item.service.CategoryService;
import com.team7.rental.item.service.CategoryService.CategoryCode;
import com.team7.rental.item.service.CategorySuggestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 카테고리 추천 (REQ-067). 등록 화면이 기다리는 동기 요청이고 트랜잭션을 열지 않는다(후보 기록만 짧은 트랜잭션).
 * <ol>
 *   <li>사진 검증(item.images 장수·형식·용량, 실패는 400) → 메모리에서 긴 변 ai.image-max-long-side로 줄인다. 저장하지 않는다</li>
 *   <li>회원당 하루 횟수(ai.category.per-member-daily)를 잡는다. 넘으면 LIMIT_EXCEEDED</li>
 *   <li>호출마다 월 한도를 먼저 잡는다(재시도 포함, 040과 같은 카운터). 넘으면 LIMIT_EXCEEDED</li>
 *   <li>응답 형식이 틀리면 ai.max-retry번까지 다시 부른다. 전체 대기는 ai.category.timeout-seconds 안. 호출 실패·시간 초과는 FAILED</li>
 *   <li>활성 카테고리 code가 아니면 ETC. ETC면 AI가 낸 후보 이름을 category_suggestions에 PENDING으로 남긴다(실패해도 응답은 그대로)</li>
 * </ol>
 * AI가 실패해도 400(사진 오류) 말고는 200으로 status만 알려 사용자가 직접 고르게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCategoryService {

	static final String FALLBACK_CODE = "ETC";

	private final AiClient aiClient;
	private final AiCategoryParser aiCategoryParser;
	private final AiUsageCounter aiUsageCounter;
	private final ImageProcessor imageProcessor;
	private final CategoryService categoryService;
	private final CategorySuggestionService categorySuggestionService;
	private final PolicyProperties policyProperties;

	public AiCategoryResponse recommend(Long memberId, List<MultipartFile> images) {
		List<AiImage> aiImages = load(images);
		try {
			if (!aiUsageCounter.tryAcquireCategory(memberId)) {
				log.info("AI category daily limit exceeded: memberId={}", memberId);
				return AiCategoryResponse.empty(AiCategoryStatus.LIMIT_EXCEEDED);
			}
			List<CategoryCode> categories = categoryService.getActiveCategoryCodes();
			if (categories.isEmpty()) {
				log.warn("AI category skipped: no active category");
				return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
			}
			return call(memberId, aiImages, categories);
		} catch (RuntimeException e) {
			// Redis·DB 등 예기치 못한 오류도 등록 흐름을 막지 않는다
			log.warn("AI category failed: memberId={}, error={}", memberId, e.toString());
			return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
		}
	}

	private AiCategoryResponse call(Long memberId, List<AiImage> aiImages, List<CategoryCode> categories) {
		List<CategoryOption> options = categories.stream()
			.map(category -> new CategoryOption(category.code(), category.name()))
			.toList();
		// 경과 시간만 재므로 Clock이 아니라 nanoTime (테스트의 고정 시각과 무관)
		long deadline = System.nanoTime()
			+ Duration.ofSeconds(policyProperties.ai().category().timeoutSeconds()).toNanos();
		int attempts = 1 + policyProperties.ai().maxRetry();
		for (int attempt = 1; attempt <= attempts; attempt++) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				log.info("AI category timed out: memberId={}, attempt={}", memberId, attempt);
				return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
			}
			if (!aiUsageCounter.tryAcquire()) {
				log.info("AI monthly limit exceeded: memberId={}", memberId);
				return AiCategoryResponse.empty(AiCategoryStatus.LIMIT_EXCEEDED);
			}
			String text;
			try {
				text = aiClient.recommendCategory(aiImages, options, Duration.ofNanos(remaining));
			} catch (RuntimeException e) {
				log.warn("AI client error: memberId={}, attempt={}, error={}", memberId, attempt, e.getMessage());
				return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
			}
			Optional<AiCategoryParser.Parsed> parsed = aiCategoryParser.parse(text);
			if (parsed.isPresent()) {
				return resolve(memberId, parsed.get(), categories);
			}
			log.info("AI category response invalid: memberId={}, attempt={}/{}", memberId, attempt, attempts);
		}
		return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
	}

	/** 목록에 있는 code면 그대로, 아니면 ETC. ETC일 때만 후보 이름을 돌려주고 기록한다 */
	private AiCategoryResponse resolve(Long memberId, AiCategoryParser.Parsed parsed, List<CategoryCode> categories) {
		Optional<CategoryCode> matched = find(categories, parsed.categoryCode());
		if (matched.isPresent() && !FALLBACK_CODE.equals(matched.get().code())) {
			CategoryCode category = matched.get();
			return AiCategoryResponse.done(category.id(), category.code(), category.name(), null);
		}
		Optional<CategoryCode> etc = find(categories, FALLBACK_CODE);
		if (etc.isEmpty()) {
			log.warn("AI category fallback missing: {} is not active", FALLBACK_CODE);
			return AiCategoryResponse.empty(AiCategoryStatus.FAILED);
		}
		String suggestedName = parsed.suggestedName();
		if (suggestedName != null) {
			recordSuggestion(memberId, suggestedName);
		}
		CategoryCode category = etc.get();
		return AiCategoryResponse.done(category.id(), category.code(), category.name(), suggestedName);
	}

	private void recordSuggestion(Long memberId, String suggestedName) {
		try {
			categorySuggestionService.recordFromAi(memberId, suggestedName);
		} catch (RuntimeException e) {
			log.warn("Category suggestion not recorded: memberId={}, error={}", memberId, e.toString());
		}
	}

	private static Optional<CategoryCode> find(List<CategoryCode> categories, String code) {
		return categories.stream().filter(category -> category.code().equals(code)).findFirst();
	}

	/** 사진 오류는 AI 실패가 아니라 입력 오류라 400으로 끝낸다(물품 등록과 같은 규칙) */
	private List<AiImage> load(List<MultipartFile> images) {
		PolicyProperties.Item.Images policy = policyProperties.item().images();
		if (images == null || images.size() < policy.min() || images.size() > policy.max()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
		int maxLongSide = policyProperties.ai().imageMaxLongSide();
		return images.stream()
			.map(file -> {
				ScaledImage scaled = imageProcessor.resizeUpload(file, policy.maxFileSize(), maxLongSide);
				return new AiImage(scaled.format().getContentType(), scaled.content());
			})
			.toList();
	}
}
