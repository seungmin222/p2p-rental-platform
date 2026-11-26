package com.team7.rental.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.ai.client.AiClientException;
import com.team7.rental.ai.client.CategoryOption;
import com.team7.rental.global.storage.TestImages;
import com.team7.rental.item.entity.CategorySuggestionStatus;
import com.team7.rental.item.repository.CategorySuggestionRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.support.FakeAiClient;
import com.team7.rental.support.IntegrationTestSupport;

/** AI 등록 보조 API (REQ-067). 실제 Redis 카운터·category_suggestions·권장 보증금 계산을 확인한다. AI는 {@link FakeAiClient} */
class AiDraftIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2099, 11, 12, 10, 0); // Redis EXPIREAT은 실제 시각과 비교하므로 미래 날짜로 고정
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String CAMPING = """
		{"categoryCode": "CAMPING", "suggestedName": null}""";
	/** categoryCode가 없어 형식 오류 (다시 부른다) */
	private static final String INVALID = """
		{"title": "텐트", "itemPrice": 200000}""";

	private static final DefaultRedisScript<Long> EXPIRE_TIME = new DefaultRedisScript<>(
		"return redis.call('EXPIRETIME', KEYS[1])", Long.class);

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CategorySuggestionRepository suggestions;

	private Member member;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		member = fixture.createMember();
	}

	@Test
	@DisplayName("목록에 있는 code면 DONE과 카테고리 id·code·이름을 주고 후보는 기록하지 않는다. 하루·월 카운터가 1씩 오른다")
	void draftListedCategory() throws Exception {
		// given
		aiClient.respondDraft(CAMPING);

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DONE"))
			.andExpect(jsonPath("$.data.categoryId").value(categoryId("CAMPING")))
			.andExpect(jsonPath("$.data.categoryCode").value("CAMPING"))
			.andExpect(jsonPath("$.data.categoryName").value("캠핑·아웃도어"))
			.andExpect(jsonPath("$.data.suggestedCategoryName").doesNotExist());
		assertThat(suggestions.count()).isZero();
		assertThat(monthlyCount()).isEqualTo("1");
		assertThat(dailyCount()).isEqualTo("1");
	}

	@Test
	@DisplayName("제목·설명·원가·대여료 초안을 주고, 보증금은 원가에 카테고리 권장 비율(CAMPING 30~50%)을 적용한다")
	void draftAllFields() throws Exception {
		// given
		aiClient.respondDraft("""
			{"categoryCode": "CAMPING", "suggestedName": null, "title": "4인용 돔 텐트",
			 "description": "폴대·팩·스트링 포함", "itemPrice": 200000, "pricePerDay": 15000}""");

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("DONE"))
			.andExpect(jsonPath("$.data.categoryCode").value("CAMPING"))
			.andExpect(jsonPath("$.data.title").value("4인용 돔 텐트"))
			.andExpect(jsonPath("$.data.description").value("폴대·팩·스트링 포함"))
			.andExpect(jsonPath("$.data.itemPrice").value(200000))
			.andExpect(jsonPath("$.data.pricePerDay").value(15000))
			.andExpect(jsonPath("$.data.minDeposit").value(60000))
			.andExpect(jsonPath("$.data.maxDeposit").value(100000));
		FakeAiClient.DraftCall call = aiClient.draftCalls().getFirst();
		assertThat(call.titleMax()).isEqualTo(50);
		assertThat(call.descriptionMax()).isEqualTo(2000);
	}

	@Test
	@DisplayName("보증금 범위는 권장 보증금 API와 같은 값이다 (ETC 30%, 원 미만 절사)")
	void depositMatchesGuide() throws Exception {
		// given
		aiClient.respondDraft("""
			{"categoryCode": "ETC", "itemPrice": 33333, "pricePerDay": 0}""");

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.categoryCode").value("ETC"))
			.andExpect(jsonPath("$.data.pricePerDay").value(0))
			.andExpect(jsonPath("$.data.minDeposit").value(9999))
			.andExpect(jsonPath("$.data.maxDeposit").value(9999));
		mockMvc.perform(get("/api/items/deposit-guide").param("categoryId", categoryId("ETC").toString())
			.param("itemPrice", "33333")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())))
			.andExpect(jsonPath("$.data.minDeposit").value(9999))
			.andExpect(jsonPath("$.data.maxDeposit").value(9999));
	}

	@Test
	@DisplayName("범위를 벗어난 원가·대여료와 빈 제목은 그 필드만 null이고 DONE. 원가가 null이면 보증금 범위도 null이며 다시 부르지 않는다")
	void invalidFieldsBecomeNull() throws Exception {
		// given
		aiClient.respondDraft("""
			{"categoryCode": "CAMERA", "suggestedName": null, "title": "  ", "description": "미러리스 카메라",
			 "itemPrice": 20000000, "pricePerDay": 500}""");

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.status").value("DONE"))
			.andExpect(jsonPath("$.data.categoryCode").value("CAMERA"))
			.andExpect(jsonPath("$.data.title").doesNotExist())
			.andExpect(jsonPath("$.data.description").value("미러리스 카메라"))
			.andExpect(jsonPath("$.data.itemPrice").doesNotExist())
			.andExpect(jsonPath("$.data.pricePerDay").doesNotExist())
			.andExpect(jsonPath("$.data.minDeposit").doesNotExist())
			.andExpect(jsonPath("$.data.maxDeposit").doesNotExist());
		assertThat(aiClient.draftCalls()).hasSize(1);
	}

	@Test
	@DisplayName("모델이 ETC가 아닌 목록 code와 후보 이름을 함께 내면 후보 이름은 버리고 기록하지 않는다")
	void dropSuggestedNameForListedCategory() throws Exception {
		// given
		aiClient.respondDraft("""
			{"categoryCode": "TOOL", "suggestedName": "전동공구"}""");

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.categoryCode").value("TOOL"))
			.andExpect(jsonPath("$.data.suggestedCategoryName").doesNotExist());
		assertThat(suggestions.count()).isZero();
	}

	@Test
	@DisplayName("목록 밖 code면 ETC와 후보 이름을 주고 category_suggestions에 PENDING 1행(물품 없음)을 남긴다")
	void unknownCodeFallsBackToEtc() throws Exception {
		// given
		aiClient.respondDraft("""
			{"categoryCode": "MUSIC", "suggestedName": " 악기 "}""");

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.status").value("DONE"))
			.andExpect(jsonPath("$.data.categoryId").value(categoryId("ETC")))
			.andExpect(jsonPath("$.data.categoryCode").value("ETC"))
			.andExpect(jsonPath("$.data.categoryName").value("기타"))
			.andExpect(jsonPath("$.data.suggestedCategoryName").value("악기"));
		assertThat(suggestions.findAll()).singleElement().satisfies(suggestion -> {
			assertThat(suggestion.getSuggestedName()).isEqualTo("악기");
			assertThat(suggestion.getMemberId()).isEqualTo(member.getId());
			assertThat(suggestion.getItemId()).isNull();
			assertThat(suggestion.getStatus()).isEqualTo(CategorySuggestionStatus.PENDING);
			assertThat(suggestion.getReviewedBy()).isNull();
		});
	}

	@Test
	@DisplayName("비활성 카테고리는 선택지로 보내지 않고, 모델이 그 code를 내도 ETC로 바꾼다")
	void inactiveCodeFallsBackToEtc() throws Exception {
		// given: categories는 테스트 사이에 비우지 않는 시드 테이블이라 끝나면 되돌린다
		jdbcTemplate.update("UPDATE categories SET is_active = FALSE WHERE code = 'CAMPING'");
		try {
			aiClient.respondDraft("""
				{"categoryCode": "CAMPING", "suggestedName": "캠핑"}""");

			// when
			draft(image())
				// then
				.andExpect(jsonPath("$.data.categoryCode").value("ETC"))
				.andExpect(jsonPath("$.data.suggestedCategoryName").value("캠핑"));
			assertThat(aiClient.draftCalls().getFirst().categories()).extracting(CategoryOption::code)
				.doesNotContain("CAMPING")
				.contains("ETC");
			assertThat(suggestions.count()).isEqualTo(1);
		} finally {
			jdbcTemplate.update("UPDATE categories SET is_active = TRUE WHERE code = 'CAMPING'");
		}
	}

	@Test
	@DisplayName("ETC에 후보 이름이 없으면 기록하지 않는다")
	void etcWithoutSuggestedName() throws Exception {
		// given
		aiClient.respondDraft(FakeAiClient.DRAFT_ETC);

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.categoryCode").value("ETC"))
			.andExpect(jsonPath("$.data.suggestedCategoryName").doesNotExist());
		assertThat(suggestions.count()).isZero();
	}

	@Test
	@DisplayName("형식 오류 2번 뒤 성공하면 DONE. 월 카운터는 호출마다 올라 3, 하루 카운터는 요청 단위라 1")
	void retryThenSucceed() throws Exception {
		// given
		aiClient.respondDraft(INVALID, "카테고리: 캠핑", CAMPING);

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.status").value("DONE"))
			.andExpect(jsonPath("$.data.categoryCode").value("CAMPING"));
		assertThat(aiClient.draftCalls()).hasSize(3);
		assertThat(monthlyCount()).isEqualTo("3");
		assertThat(dailyCount()).isEqualTo("1");
	}

	@Test
	@DisplayName("형식 오류가 3번 이어지면 FAILED이고 모든 필드가 null. 월 카운터 3, 후보 기록 없음")
	void retryExhausted() throws Exception {
		// given
		aiClient.respondDraft(INVALID, INVALID, INVALID);

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("FAILED"))
			.andExpect(jsonPath("$.data.categoryId").doesNotExist())
			.andExpect(jsonPath("$.data.categoryCode").doesNotExist())
			.andExpect(jsonPath("$.data.categoryName").doesNotExist())
			.andExpect(jsonPath("$.data.suggestedCategoryName").doesNotExist())
			.andExpect(jsonPath("$.data.title").doesNotExist())
			.andExpect(jsonPath("$.data.itemPrice").doesNotExist())
			.andExpect(jsonPath("$.data.minDeposit").doesNotExist());
		assertThat(aiClient.draftCalls()).hasSize(3);
		assertThat(monthlyCount()).isEqualTo("3");
		assertThat(suggestions.count()).isZero();
	}

	@Test
	@DisplayName("호출이 실패하면 다시 부르지 않고 FAILED. 그 호출은 월 카운터에 센다")
	void clientFailure() throws Exception {
		// given
		aiClient.failDraft(new AiClientException("API 오류"));

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("FAILED"));
		assertThat(aiClient.draftCalls()).hasSize(1);
		assertThat(monthlyCount()).isEqualTo("1");
		assertThat(suggestions.count()).isZero();
	}

	@Test
	@DisplayName("월 한도를 넘으면 부르지 않고 LIMIT_EXCEEDED")
	void monthlyLimitExceeded() throws Exception {
		// given
		redis.opsForValue().set(monthlyKey(), "1000");

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.data.categoryId").doesNotExist());
		assertThat(aiClient.draftCalls()).isEmpty();
		assertThat(dailyCount()).isEqualTo("1");
	}

	@Test
	@DisplayName("재시도 중 월 한도에 닿으면 그 시도는 부르지 않고 LIMIT_EXCEEDED")
	void monthlyLimitReachedDuringRetry() throws Exception {
		// given
		redis.opsForValue().set(monthlyKey(), "999");
		aiClient.respondDraft(INVALID, CAMPING);

		// when
		draft(image())
			// then
			.andExpect(jsonPath("$.data.status").value("LIMIT_EXCEEDED"));
		assertThat(aiClient.draftCalls()).hasSize(1);
	}

	@Test
	@DisplayName("회원 하루 11번째 요청은 LIMIT_EXCEEDED이고 월 카운터를 쓰지 않는다. 다른 회원은 영향 없다")
	void dailyLimitExceeded() throws Exception {
		// given: 10번은 성공
		for (int i = 0; i < 10; i++) {
			draft(image()).andExpect(jsonPath("$.data.status").value("DONE"));
		}
		assertThat(monthlyCount()).isEqualTo("10");

		// when
		draft(image())
			// then
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("LIMIT_EXCEEDED"));
		assertThat(aiClient.draftCalls()).hasSize(10);
		assertThat(monthlyCount()).isEqualTo("10");
		assertThat(dailyCount()).isEqualTo("11");

		Member other = fixture.createMember();
		draft(other, image()).andExpect(jsonPath("$.data.status").value("DONE"));
	}

	@Test
	@DisplayName("하루 카운터는 날짜별 키라 다음 날이면 다시 쓸 수 있다")
	void dailyLimitResetsNextDay() throws Exception {
		// given
		redis.opsForValue().set(dailyKey(NOW.toLocalDate()), "10");
		draft(image()).andExpect(jsonPath("$.data.status").value("LIMIT_EXCEEDED"));

		// when
		testClock.fixAt(NOW.plusDays(1));

		// then
		draft(image()).andExpect(jsonPath("$.data.status").value("DONE"));
	}

	@Test
	@DisplayName("월 카운터는 다음 달 2일 0시, 하루 카운터는 모레 0시(Asia/Seoul)에 만료된다")
	void counterExpiry() throws Exception {
		// when
		draft(image()).andExpect(jsonPath("$.data.status").value("DONE"));

		// then: TTL은 실제 시각 기준이라 절대 만료 시각(EXPIRETIME, Redis 7)으로 본다
		assertThat(expireTime(monthlyKey()))
			.isEqualTo(LocalDate.of(2099, 12, 2).atStartOfDay(SEOUL).toEpochSecond());
		assertThat(expireTime(dailyKey(NOW.toLocalDate())))
			.isEqualTo(LocalDate.of(2099, 11, 14).atStartOfDay(SEOUL).toEpochSecond());
	}

	@Test
	@DisplayName("큰 사진은 긴 변 1024px로 줄여 보내고, 활성 카테고리 전체를 선택지로 넘긴다")
	void downscaleAndSendCategories() throws Exception {
		// when
		draft(new MockMultipartFile("images", "big.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg(1600, 800)),
			image())
			.andExpect(jsonPath("$.data.status").value("DONE"));

		// then
		FakeAiClient.DraftCall call = aiClient.draftCalls().getFirst();
		assertThat(call.images()).hasSize(2);
		BufferedImage sent = decode(call.images().getFirst().data());
		assertThat(sent.getWidth()).isEqualTo(1024);
		assertThat(sent.getHeight()).isEqualTo(512);
		assertThat(call.categories()).extracting(CategoryOption::code)
			.containsExactly("CAMPING", "TOOL", "CAMERA", "BABY", "PARTY", "HOBBY", "APPLIANCE", "ETC");
	}

	@Test
	@DisplayName("사진이 없으면 400 IMAGE_COUNT_INVALID, 카운터를 쓰지 않는다")
	void noImage() throws Exception {
		draft().andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));
		assertNothingCounted();
	}

	@Test
	@DisplayName("사진이 11장이면 400 IMAGE_COUNT_INVALID, 카운터를 쓰지 않는다")
	void tooManyImages() throws Exception {
		MockMultipartFile[] images = new MockMultipartFile[11];
		for (int i = 0; i < images.length; i++) {
			images[i] = image();
		}
		draft(images).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));
		assertNothingCounted();
	}

	@Test
	@DisplayName("jpg·png가 아니면 400 IMAGE_INVALID_TYPE, 카운터를 쓰지 않는다")
	void invalidType() throws Exception {
		MockMultipartFile text = new MockMultipartFile("images", "a.jpg", MediaType.IMAGE_JPEG_VALUE,
			"not an image".getBytes(StandardCharsets.UTF_8));
		draft(image(), text).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_INVALID_TYPE"));
		assertNothingCounted();
	}

	@Test
	@DisplayName("장당 10MB를 넘으면 400 IMAGE_SIZE_EXCEEDED, 카운터를 쓰지 않는다")
	void sizeExceeded() throws Exception {
		MockMultipartFile big = new MockMultipartFile("images", "big.jpg", MediaType.IMAGE_JPEG_VALUE,
			new byte[10 * 1024 * 1024 + 1]);
		draft(big).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_SIZE_EXCEEDED"));
		assertNothingCounted();
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void unauthorized() throws Exception {
		mockMvc.perform(multipart("/api/items/ai-draft").file(image()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
		assertThat(aiClient.draftCalls()).isEmpty();
	}

	private ResultActions draft(MockMultipartFile... images) throws Exception {
		return draft(member, images);
	}

	private ResultActions draft(Member requester, MockMultipartFile... images) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/items/ai-draft");
		for (MockMultipartFile image : images) {
			request.file(image);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(requester.getId())));
	}

	private void assertNothingCounted() {
		assertThat(aiClient.draftCalls()).isEmpty();
		assertThat(redis.hasKey(monthlyKey())).isFalse();
		assertThat(redis.hasKey(dailyKey(NOW.toLocalDate()))).isFalse();
		assertThat(suggestions.count()).isZero();
	}

	private Long categoryId(String code) {
		return jdbcTemplate.queryForObject("SELECT id FROM categories WHERE code = ?", Long.class, code);
	}

	private String monthlyKey() {
		return AiUsageCounter.key(YearMonth.from(NOW));
	}

	private String dailyKey(LocalDate day) {
		return AiUsageCounter.draftKey(member.getId(), day);
	}

	private String monthlyCount() {
		return redis.opsForValue().get(monthlyKey());
	}

	private String dailyCount() {
		return redis.opsForValue().get(dailyKey(NOW.toLocalDate()));
	}

	/** Redisson 연결은 임의 명령(execute)을 지원하지 않아 Lua로 부른다 */
	private Long expireTime(String key) {
		return redis.execute(EXPIRE_TIME, List.of(key));
	}

	private static MockMultipartFile image() {
		return new MockMultipartFile("images", "item.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg(40, 30));
	}

	private static BufferedImage decode(byte[] content) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(content));
	}
}
