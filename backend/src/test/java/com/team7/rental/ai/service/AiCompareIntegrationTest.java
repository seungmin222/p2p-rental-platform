package com.team7.rental.ai.service;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.team7.rental.ai.client.AiClientException;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.TestImages;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.AiChangeType;
import com.team7.rental.rental.entity.AiCompareStatus;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.InspectionResult;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalAiCompareService;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeAiClient;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * AI 물품 상태 비교 워커 (REQ-040). 큐를 거치지 않고 {@link AiCompareService#compare}를 직접 부른다(큐 경로는 AiCompareQueueIntegrationTest).
 * 인계·반납은 실제 API로 올려 사진 파일이 저장소에 있다.
 */
class AiCompareIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";

	@Autowired
	private AiCompareService aiCompareService;

	@Autowired
	private RentalAiCompareService rentalAiCompareService;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private FileStorage fileStorage;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Member owner;
	private Member renter;
	private Item item;

	@BeforeEach
	void setUp() {
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
	}

	@Test
	@DisplayName("반납 요청 뒤 비교하면 인계·반납 사진으로 한 번 부르고 DONE 결과가 조회되며 양측에 AI_COMPARE_DONE이 간다")
	void compareDone() throws Exception {
		// given
		Rental rental = returnedRental(2, 1);

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).singleElement().satisfies(call -> {
			assertThat(call.handoverImages()).hasSize(2);
			assertThat(call.returnImages()).hasSize(1);
			assertThat(call.handoverImages())
				.allSatisfy(image -> assertThat(image.mediaType()).isEqualTo("image/jpeg"));
		});
		ItemInspection returned = returnInspection(rental);
		assertThat(returned.getAiResult().status()).isEqualTo(AiCompareStatus.DONE);
		assertThat(returned.getAiResult().model()).isEqualTo("fake-model");
		assertThat(returned.getAiResult().result().changeTypes()).containsExactly(AiChangeType.SCRATCH);

		inspections(rental, renter)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].phase").value("HANDOVER"))
			.andExpect(jsonPath("$.data[0].aiResult").doesNotExist())
			.andExpect(jsonPath("$.data[1].aiResult.status").value("DONE"))
			.andExpect(jsonPath("$.data[1].aiResult.result.changed").value(true))
			.andExpect(jsonPath("$.data[1].aiResult.result.changeTypes[0]").value("SCRATCH"))
			.andExpect(jsonPath("$.data[1].aiResult.result.confidence").value(0.8))
			.andExpect(jsonPath("$.data[1].aiResult.result.regions[0].imageIndex").value(0))
			.andExpect(jsonPath("$.data[1].aiResult.analyzedAt").value("2026-11-05T18:00:00"));

		String eventId = "AI_COMPARE_DONE:" + rental.getId();
		assertThat(notificationPort.sent()).containsExactlyInAnyOrder(
			new Sent(renter.getId(), Type.AI_COMPARE_DONE, TargetType.RENTAL, rental.getId(), eventId),
			new Sent(owner.getId(), Type.AI_COMPARE_DONE, TargetType.RENTAL, rental.getId(), eventId));
		assertThat(redis.opsForValue().get(usageKey())).isEqualTo("1");
	}

	@Test
	@DisplayName("같은 대여를 두 번 비교해도 호출·알림은 한 번이다 (대여 건당 1회)")
	void compareOncePerRental() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		aiCompareService.compare(rental.getId());

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).hasSize(1);
		assertThat(notificationPort.sent()).hasSize(2);
	}

	@Test
	@DisplayName("응답 형식이 틀리면 최대 2회 다시 불러 세 번째에 맞으면 DONE이고 사용량은 3건이다")
	void retryOnInvalidResponse() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		aiClient.respond(FakeAiClient.INVALID, "not json", FakeAiClient.VALID);

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).hasSize(3);
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.DONE);
		assertThat(redis.opsForValue().get(usageKey())).isEqualTo("3");
		assertThat(redis.getExpire(usageKey())).isPositive();
	}

	@Test
	@DisplayName("세 번 모두 형식이 틀리면 FAILED이고 알림을 보내지 않으며 대여 상태는 그대로다")
	void failAfterRetries() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		aiClient.respond(FakeAiClient.INVALID, FakeAiClient.INVALID, FakeAiClient.INVALID, FakeAiClient.VALID);

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).hasSize(3);
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.FAILED);
		assertThat(notificationPort.sent()).isEmpty();
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RETURNED);
		inspections(rental, owner)
			.andExpect(jsonPath("$.data[1].aiResult.status").value("FAILED"))
			.andExpect(jsonPath("$.data[1].aiResult.result").doesNotExist());
	}

	@Test
	@DisplayName("호출이 실패하면 다시 부르지 않고 FAILED이며 제공자는 그대로 상태 확인을 할 수 있다")
	void clientErrorDoesNotBlockReturn() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		aiClient.fail(new AiClientException("Claude 호출 실패"));

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).hasSize(1);
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.FAILED);
		confirmNormal(rental)
			.andExpect(status().isOk());
		assertThat(returnInspection(rental).getResult()).isEqualTo(InspectionResult.NORMAL);
	}

	@Test
	@DisplayName("월 한도(1,000건)를 다 썼으면 부르지 않고 LIMIT_EXCEEDED이며 알리지 않는다")
	void monthlyLimitExceeded() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		redis.opsForValue().set(usageKey(), "1000");

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).isEmpty();
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.LIMIT_EXCEEDED);
		assertThat(notificationPort.sent()).isEmpty();
		inspections(rental, renter)
			.andExpect(jsonPath("$.data[1].aiResult.status").value("LIMIT_EXCEEDED"));
	}

	@Test
	@DisplayName("재시도 중에 한도에 닿으면 그 뒤로는 부르지 않고 LIMIT_EXCEEDED다 (재시도도 1건)")
	void limitReachedDuringRetry() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		redis.opsForValue().set(usageKey(), "999");
		aiClient.respond(FakeAiClient.INVALID);

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).hasSize(1);
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.LIMIT_EXCEEDED);
	}

	@Test
	@DisplayName("비교 전에는 aiResult가 없고, 결과를 기다리지 않고 상태 확인할 수 있다")
	void confirmWithoutAiResult() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);

		// when, then
		inspections(rental, renter)
			.andExpect(jsonPath("$.data[1].phase").value("RETURN"))
			.andExpect(jsonPath("$.data[1].aiResult").doesNotExist());
		confirmNormal(rental)
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("상태 확인 뒤에 온 결과도 저장되고 판정은 그대로다")
	void resultAfterJudgement() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		confirmNormal(rental).andExpect(status().isOk());

		// when
		aiCompareService.compare(rental.getId());

		// then
		ItemInspection returned = returnInspection(rental);
		assertThat(returned.getResult()).isEqualTo(InspectionResult.NORMAL);
		assertThat(returned.getAiResult().status()).isEqualTo(AiCompareStatus.DONE);
	}

	@Test
	@DisplayName("맡은 뒤 끝나지 않은 PROCESSING은 10분까지 PROCESSING, 넘으면 응답에서 FAILED다")
	void staleProcessingShownAsFailed() throws Exception {
		// given: 워커가 맡은 뒤 죽었다
		Rental rental = returnedRental(1, 1);
		rentalAiCompareService.start(rental.getId());

		// when, then
		testClock.advance(Duration.ofMinutes(10));
		inspections(rental, renter)
			.andExpect(jsonPath("$.data[1].aiResult.status").value("PROCESSING"))
			.andExpect(jsonPath("$.data[1].aiResult.analyzedAt").doesNotExist());
		testClock.advance(Duration.ofMinutes(1));
		inspections(rental, renter)
			.andExpect(jsonPath("$.data[1].aiResult.status").value("FAILED"));
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.PROCESSING);

		// 다시 온 메시지도 맡지 않는다
		aiCompareService.compare(rental.getId());
		assertThat(aiClient.calls()).isEmpty();
	}

	@Test
	@DisplayName("미반납 신고 건은 비교하지 않는다")
	void skipLostReported() throws Exception {
		// given: 종료일 + 7일이 지난 연체 건 (신고는 11/13부터)
		testClock.fixAt(LocalDateTime.of(2026, 11, 13, 9, 0));
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START, END);
		mockMvc.perform(post("/api/rentals/{id}/lost-report", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).isEmpty();
		assertThat(returnInspection(rental).getAiResult()).isNull();
	}

	@Test
	@DisplayName("사진 파일을 읽지 못하면 부르지 않고 FAILED다")
	void missingImageFails() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);
		String url = returnInspection(rental).getImageUrls().getFirst();
		fileStorage.delete(fileStorage.keyOf(url));

		// when
		aiCompareService.compare(rental.getId());

		// then
		assertThat(aiClient.calls()).isEmpty();
		assertThat(returnInspection(rental).getAiResult().status()).isEqualTo(AiCompareStatus.FAILED);
	}

	@Test
	@DisplayName("큰 사진은 긴 변 1024px로 줄여서 보낸다")
	void downscaleBeforeSending() throws Exception {
		// given
		Rental rental = returnedRental(1, 0);
		returnWith(rental, new MockMultipartFile("images", "big.jpg", MediaType.IMAGE_JPEG_VALUE,
			TestImages.jpeg(1600, 800)));

		// when
		aiCompareService.compare(rental.getId());

		// then
		BufferedImage sent = decode(aiClient.calls().getFirst().returnImages().getFirst().data());
		assertThat(sent.getWidth()).isEqualTo(1024);
		assertThat(sent.getHeight()).isEqualTo(512);
	}

	@Test
	@DisplayName("상태 확인 트랜잭션이 기록을 먼저 읽은 사이에 AI 결과가 저장돼도 판정 커밋이 AI 결과를 지우지 않는다")
	void judgementDoesNotOverwriteAiResult() throws Exception {
		// given
		Rental rental = returnedRental(1, 1);

		// when: 판정 트랜잭션이 기록을 읽고 → 그 사이 워커가 결과를 저장(별도 트랜잭션) → 판정 커밋
		transactionTemplate.executeWithoutResult(status -> {
			ItemInspection stale = inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN)
				.orElseThrow();
			Thread worker = new Thread(() -> aiCompareService.compare(rental.getId()));
			worker.start();
			try {
				worker.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			stale.judge(InspectionResult.NORMAL, null, java.util.List.of());
		});

		// then
		ItemInspection returned = returnInspection(rental);
		assertThat(returned.getResult()).isEqualTo(InspectionResult.NORMAL);
		assertThat(returned.getAiResult()).isNotNull();
		assertThat(returned.getAiResult().status()).isEqualTo(AiCompareStatus.DONE);
	}

	/** 인계(사진 handoverImages장) → 반납 요청(사진 returnImages장, 0이면 반납 요청 안 함). 시각은 반납 시각에 고정 */
	private Rental returnedRental(int handoverImages, int returnImages) throws Exception {
		testClock.fixAt(START.atTime(10, 0));
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
		handover(rental, handoverImages);
		testClock.fixAt(RETURNED_AT);
		if (returnImages > 0) {
			MockMultipartFile[] images = new MockMultipartFile[returnImages];
			for (int i = 0; i < returnImages; i++) {
				images[i] = image();
			}
			returnWith(rental, images);
		}
		return rental;
	}

	private void handover(Rental rental, int images) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH,
			"/api/rentals/{rentalId}/handover", rental.getId());
		for (int i = 0; i < images; i++) {
			request.file(image());
		}
		mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());
	}

	private void returnWith(Rental rental, MockMultipartFile... images) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/rentals/{rentalId}/return-request",
			rental.getId());
		request.file(json(ALL_TRUE));
		for (MockMultipartFile image : images) {
			request.file(image);
		}
		mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isOk());
	}

	private ResultActions confirmNormal(Rental rental) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", rental.getId())
			.file(json("{\"result\": \"NORMAL\"}"))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private ResultActions inspections(Rental rental, Member member) throws Exception {
		return mockMvc.perform(get("/api/rentals/{rentalId}/inspections", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}

	private ItemInspection returnInspection(Rental rental) {
		return inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN).orElseThrow();
	}

	private String usageKey() {
		return AiUsageCounter.key(YearMonth.from(RETURNED_AT));
	}

	private static BufferedImage decode(byte[] data) throws IOException {
		return ImageIO.read(new ByteArrayInputStream(data));
	}
}
