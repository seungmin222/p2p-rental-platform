package com.team7.rental.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.ai.client.AiClient;
import com.team7.rental.ai.client.AiImage;
import com.team7.rental.ai.client.CategoryOption;
import com.team7.rental.ai.dto.AiDraftResponse;
import com.team7.rental.ai.dto.AiDraftStatus;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ScaledImage;
import com.team7.rental.item.dto.ItemDepositGuideRequest;
import com.team7.rental.item.dto.ItemDepositGuideResponse;
import com.team7.rental.item.service.CategoryService;
import com.team7.rental.item.service.CategoryService.CategoryCode;
import com.team7.rental.item.service.CategorySuggestionService;
import com.team7.rental.item.service.ItemQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 등록 보조 (REQ-067): 사진 → 카테고리·제목·설명 초안·추정 원가·일 대여료 제안 + 권장 보증금 범위. 등록 화면이 기다리는 동기 요청이고 트랜잭션을 열지
 * 않는다(후보 기록·보증금 계산만 짧게 DB를 읽고 쓴다).
 * <ol>
 *   <li>사진 검증(item.images 장수·형식·용량, 실패는 400) → 메모리에서 긴 변 ai.image-max-long-side로 줄인다. 저장하지 않는다</li>
 *   <li>회원당 하루 횟수(ai.draft.per-member-daily)를 잡는다. 넘으면 LIMIT_EXCEEDED</li>
 *   <li>호출마다 월 한도를 먼저 잡는다(재시도 포함, 040과 같은 카운터). 넘으면 LIMIT_EXCEEDED</li>
 *   <li>응답이 JSON이 아니거나 categoryCode가 없으면 ai.max-retry번까지 다시 부른다. 전체 대기는 ai.draft.timeout-seconds 안. 호출
 *   실패·시간 초과는 FAILED</li>
 *   <li>활성 카테고리 code가 아니면 ETC. ETC면 AI가 낸 후보 이름을 category_suggestions에 PENDING으로 남긴다(실패해도 응답은 그대로)</li>
 *   <li>보증금은 AI에게 묻지 않는다. 추정 원가가 있으면 권장 보증금 API(deposit-guide)와 같은 규칙으로 범위를 계산한다(실패하면 null)</li>
 * </ol>
 * AI가 실패해도 400(사진 오류) 말고는 200으로 status만 알려 사용자가 직접 입력하게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDraftService {

	static final String FALLBACK_CODE = "ETC";

	private final AiClient aiClient;
	private final AiDraftParser aiDraftParser;
	private final AiUsageCounter aiUsageCounter;
	private final ImageProcessor imageProcessor;
	private final CategoryService categoryService;
	private final CategorySuggestionService categorySuggestionService;
	private final ItemQueryService itemQueryService;
	private final PolicyProperties policyProperties;

	public AiDraftResponse draft(Long memberId, List<MultipartFile> images) {
		List<AiImage> aiImages = load(images);
		try {
			if (!aiUsageCounter.tryAcquireDraft(memberId)) {
				log.info("AI draft daily limit exceeded: memberId={}", memberId);
				return AiDraftResponse.empty(AiDraftStatus.LIMIT_EXCEEDED);
			}
			List<CategoryCode> categories = categoryService.getActiveCategoryCodes();
			if (categories.isEmpty()) {
				log.warn("AI draft skipped: no active category");
				return AiDraftResponse.empty(AiDraftStatus.FAILED);
			}
			return call(memberId, aiImages, categories);
		} catch (RuntimeException e) {
			// Redis·DB 등 예기치 못한 오류도 등록 흐름을 막지 않는다
			log.warn("AI draft failed: memberId={}, error={}", memberId, e.toString());
			return AiDraftResponse.empty(AiDraftStatus.FAILED);
		}
	}

	private AiDraftResponse call(Long memberId, List<AiImage> aiImages, List<CategoryCode> categories) {
		List<CategoryOption> options = categories.stream()
			.map(category -> new CategoryOption(category.code(), category.name()))
			.toList();
		PolicyProperties.Item item = policyProperties.item();
		// 경과 시간만 재므로 Clock이 아니라 nanoTime (테스트의 고정 시각과 무관)
		long deadline = System.nanoTime()
			+ Duration.ofSeconds(policyProperties.ai().draft().timeoutSeconds()).toNanos();
		int attempts = 1 + policyProperties.ai().maxRetry();
		for (int attempt = 1; attempt <= attempts; attempt++) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				log.info("AI draft timed out: memberId={}, attempt={}", memberId, attempt);
				return AiDraftResponse.empty(AiDraftStatus.FAILED);
			}
			if (!aiUsageCounter.tryAcquire()) {
				log.info("AI monthly limit exceeded: memberId={}", memberId);
				return AiDraftResponse.empty(AiDraftStatus.LIMIT_EXCEEDED);
			}
			String text;
			try {
				text = aiClient.draft(aiImages, options, item.nameMax(), item.descriptionMax(),
					Duration.ofNanos(remaining));
			} catch (RuntimeException e) {
				log.warn("AI client error: memberId={}, attempt={}, error={}", memberId, attempt, e.getMessage());
				return AiDraftResponse.empty(AiDraftStatus.FAILED);
			}
			Optional<AiDraftParser.Parsed> parsed = aiDraftParser.parse(text);
			if (parsed.isPresent()) {
				return resolve(memberId, parsed.get(), categories);
			}
			log.info("AI draft response invalid: memberId={}, attempt={}/{}", memberId, attempt, attempts);
		}
		return AiDraftResponse.empty(AiDraftStatus.FAILED);
	}

	/** 카테고리를 정하고(목록에 없으면 ETC, ETC일 때만 후보 이름) 추정 원가로 권장 보증금 범위를 붙인다 */
	private AiDraftResponse resolve(Long memberId, AiDraftParser.Parsed parsed, List<CategoryCode> categories) {
		Optional<CategoryCode> matched = find(categories, parsed.categoryCode());
		CategoryCode category;
		String suggestedName = null;
		if (matched.isPresent() && !FALLBACK_CODE.equals(matched.get().code())) {
			category = matched.get();
		} else {
			Optional<CategoryCode> etc = find(categories, FALLBACK_CODE);
			if (etc.isEmpty()) {
				log.warn("AI draft category fallback missing: {} is not active", FALLBACK_CODE);
				return AiDraftResponse.empty(AiDraftStatus.FAILED);
			}
			category = etc.get();
			suggestedName = parsed.suggestedName();
			if (suggestedName != null) {
				recordSuggestion(memberId, suggestedName);
			}
		}
		ItemDepositGuideResponse deposit = depositGuide(category.id(), parsed.itemPrice());
		return new AiDraftResponse(AiDraftStatus.DONE, category.id(), category.code(), category.name(), suggestedName,
			parsed.title(), parsed.description(), parsed.itemPrice(), parsed.pricePerDay(),
			deposit == null ? null : deposit.minDeposit(), deposit == null ? null : deposit.maxDeposit());
	}

	/** 권장 보증금 API와 같은 계산. 원가가 없거나 계산이 실패하면 null(초안의 나머지는 그대로 준다) */
	private ItemDepositGuideResponse depositGuide(Long categoryId, Integer itemPrice) {
		if (itemPrice == null) {
			return null;
		}
		try {
			return itemQueryService.getDepositGuide(new ItemDepositGuideRequest(categoryId, itemPrice));
		} catch (RuntimeException e) {
			log.warn("AI draft deposit guide failed: categoryId={}, error={}", categoryId, e.toString());
			return null;
		}
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
