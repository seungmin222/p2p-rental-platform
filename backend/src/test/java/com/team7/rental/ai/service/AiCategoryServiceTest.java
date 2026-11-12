package com.team7.rental.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.ai.client.AiClient;
import com.team7.rental.ai.dto.AiCategoryResponse;
import com.team7.rental.ai.dto.AiCategoryStatus;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.ImageFormat;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ScaledImage;
import com.team7.rental.item.service.CategoryService;
import com.team7.rental.item.service.CategoryService.CategoryCode;
import com.team7.rental.item.service.CategorySuggestionService;

import tools.jackson.databind.json.JsonMapper;

/**
 * AI 카테고리 추천 서비스 (REQ-067) 중 통합 테스트로 만들기 어려운 경우: 전체 제한 시간(nanoTime 기준)과 Redis·기록 실패.
 * 정상 흐름·카운터·400은 AiCategoryIntegrationTest.
 */
class AiCategoryServiceTest {

	private static final long MEMBER_ID = 1L;
	private static final String INVALID = "형식이 틀린 응답";
	private static final List<CategoryCode> CATEGORIES = List.of(new CategoryCode(1L, "CAMPING", "캠핑·아웃도어"),
		new CategoryCode(8L, "ETC", "기타"));

	private final AiClient aiClient = mock(AiClient.class);
	private final AiUsageCounter usageCounter = mock(AiUsageCounter.class);
	private final ImageProcessor imageProcessor = mock(ImageProcessor.class);
	private final CategoryService categoryService = mock(CategoryService.class);
	private final CategorySuggestionService suggestionService = mock(CategorySuggestionService.class);
	private final List<MultipartFile> images = List.of(
		new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[] {1}));

	@BeforeEach
	void setUp() {
		when(imageProcessor.resizeUpload(any(), any(), anyInt()))
			.thenReturn(new ScaledImage(ImageFormat.JPEG, new byte[] {1}));
		when(categoryService.getActiveCategoryCodes()).thenReturn(CATEGORIES);
		when(usageCounter.tryAcquireCategory(MEMBER_ID)).thenReturn(true);
		when(usageCounter.tryAcquire()).thenReturn(true);
	}

	@Test
	@DisplayName("형식 오류 재시도 전에 전체 제한 시간이 다 지났으면 더 부르지 않고 FAILED, 월 카운터도 더 쓰지 않는다")
	void timeoutBeforeRetry() {
		// given: 제한 1초, 첫 호출이 1초 넘게 걸리고 형식이 틀림
		AiCategoryService service = service(1);
		when(aiClient.recommendCategory(any(), any(), any())).thenAnswer(invocation -> {
			Thread.sleep(1_100);
			return INVALID;
		});

		// when
		AiCategoryResponse response = service.recommend(MEMBER_ID, images);

		// then
		assertThat(response).isEqualTo(AiCategoryResponse.empty(AiCategoryStatus.FAILED));
		verify(aiClient, times(1)).recommendCategory(any(), any(), any());
		verify(usageCounter, times(1)).tryAcquire();
		verifyNoInteractions(suggestionService);
	}

	@Test
	@DisplayName("시도마다 남은 시간을 넘기고, 그 값은 전체 제한 시간을 넘지 않으며 줄어든다")
	void passRemainingTime() {
		// given
		AiCategoryService service = service(20);
		when(aiClient.recommendCategory(any(), any(), any())).thenReturn(INVALID)
			.thenReturn("{\"categoryCode\": \"CAMPING\", \"suggestedName\": null}");

		// when
		AiCategoryResponse response = service.recommend(MEMBER_ID, images);

		// then
		assertThat(response.status()).isEqualTo(AiCategoryStatus.DONE);
		ArgumentCaptor<Duration> timeouts = ArgumentCaptor.forClass(Duration.class);
		verify(aiClient, times(2)).recommendCategory(any(), any(), timeouts.capture());
		Duration first = timeouts.getAllValues().get(0);
		Duration second = timeouts.getAllValues().get(1);
		assertThat(first).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20));
		assertThat(second).isPositive().isLessThan(first);
	}

	@Test
	@DisplayName("Redis 카운터 오류는 FAILED로 끝내고 AI를 부르지 않는다")
	void redisFailure() {
		// given
		AiCategoryService service = service(20);
		when(usageCounter.tryAcquireCategory(MEMBER_ID)).thenThrow(new QueryTimeoutException("redis down"));

		// when
		AiCategoryResponse response = service.recommend(MEMBER_ID, images);

		// then
		assertThat(response).isEqualTo(AiCategoryResponse.empty(AiCategoryStatus.FAILED));
		verifyNoInteractions(aiClient);
	}

	@Test
	@DisplayName("후보 기록이 실패해도 추천 응답은 그대로 준다")
	void suggestionRecordFailure() {
		// given
		AiCategoryService service = service(20);
		when(aiClient.recommendCategory(any(), any(), any()))
			.thenReturn("{\"categoryCode\": \"MUSIC\", \"suggestedName\": \"악기\"}");
		doThrow(new IllegalStateException("db down")).when(suggestionService).recordFromAi(MEMBER_ID, "악기");

		// when
		AiCategoryResponse response = service.recommend(MEMBER_ID, images);

		// then
		assertThat(response).isEqualTo(AiCategoryResponse.done(8L, "ETC", "기타", "악기"));
	}

	@Test
	@DisplayName("활성 목록에 ETC가 없으면 목록 밖 code는 FAILED이고 후보를 기록하지 않는다")
	void etcMissing() {
		// given
		AiCategoryService service = service(20);
		when(categoryService.getActiveCategoryCodes()).thenReturn(List.of(CATEGORIES.getFirst()));
		when(aiClient.recommendCategory(any(), any(), any()))
			.thenReturn("{\"categoryCode\": \"MUSIC\", \"suggestedName\": \"악기\"}");

		// when
		AiCategoryResponse response = service.recommend(MEMBER_ID, images);

		// then
		assertThat(response).isEqualTo(AiCategoryResponse.empty(AiCategoryStatus.FAILED));
		verifyNoInteractions(suggestionService);
	}

	private AiCategoryService service(int timeoutSeconds) {
		PolicyProperties policies = mock(PolicyProperties.class);
		when(policies.ai()).thenReturn(new PolicyProperties.Ai(1000, 2, 1024, new PolicyProperties.Ai.Compare(10),
			new PolicyProperties.Ai.Category(timeoutSeconds, 10)));
		when(policies.item()).thenReturn(new PolicyProperties.Item(50, 2000,
			new PolicyProperties.Item.Amount(1000, 1000000), new PolicyProperties.Item.Amount(1000, 3000000),
			10000000, new PolicyProperties.Item.Images(1, 10, DataSize.ofMegabytes(10))));
		return new AiCategoryService(aiClient, new AiCategoryParser(JsonMapper.builder().build()), usageCounter,
			imageProcessor, categoryService, suggestionService, policies);
	}
}
