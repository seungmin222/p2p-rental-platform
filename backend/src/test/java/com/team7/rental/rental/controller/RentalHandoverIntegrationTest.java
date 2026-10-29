package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** PATCH /api/rentals/{id}/handover (REQ-017 인계 확인) */
class RentalHandoverIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = START.atTime(10, 0);

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
	}

	@Test
	@DisplayName("제공자가 시작일 당일 사진·메모로 인계하면 200 RENTING이고 인계 시각·기록·이력이 남는다")
	void handover() throws Exception {
		// given
		long storedBefore = storedImageCount();

		// when, then
		handover(owner, json("{\"memo\": \"  충전기 포함 \"}"), image(), image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RENTING"))
			.andExpect(jsonPath("$.data.myRole").value("OWNER"))
			.andExpect(jsonPath("$.data.handedOverAt").value("2026-11-03T10:00:00"));

		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getHandedOverAt()).isEqualTo(NOW);
		assertThat(inspectionRepository.findAll()).singleElement().satisfies(inspection -> {
			assertThat(inspection.getRentalId()).isEqualTo(rental.getId());
			assertThat(inspection.getPhase()).isEqualTo(InspectionPhase.HANDOVER);
			assertThat(inspection.getWrittenBy()).isEqualTo(owner.getId());
			assertThat(inspection.getNote()).isEqualTo("충전기 포함");
			assertThat(inspection.getImageUrls()).hasSize(2)
				.allSatisfy(url -> assertThat(url).startsWith("/files/inspections/").endsWith(".jpg"));
			assertThat(inspection.getChecklist()).isNull();
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RESERVED);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RENTING);
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_HANDOVER);
			assertThat(history.getChangedAt()).isEqualTo(NOW);
		});
		assertThat(storedImageCount()).isEqualTo(storedBefore + 2);
	}

	@Test
	@DisplayName("request part를 생략해도 사진만으로 인계된다(메모 없음)")
	void handoverWithoutRequestPart() throws Exception {
		// when, then
		handover(owner, image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RENTING"));

		assertThat(inspectionRepository.findAll()).singleElement()
			.extracting(ItemInspection::getNote)
			.isNull();
	}

	@Test
	@DisplayName("시작일 이후에도 인계할 수 있다")
	void handoverAfterStart() throws Exception {
		// given
		testClock.fixAt(END.atTime(20, 0));

		// when, then
		handover(owner, image()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("시작일 전날 23:59:59에 인계하면 409 RENTAL_HANDOVER_TOO_EARLY이고 사진을 저장하지 않는다")
	void handoverTooEarly() throws Exception {
		// given
		testClock.fixAt(START.minusDays(1).atTime(23, 59, 59));
		long storedBefore = storedImageCount();

		// when, then
		handover(owner, image())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_HANDOVER_TOO_EARLY"));

		assertUnchanged();
		assertThat(storedImageCount()).isEqualTo(storedBefore);
	}

	@Test
	@DisplayName("대여자가 인계하면 403 RENTAL_OWNER_ONLY다")
	void handoverByRenter() throws Exception {
		// when, then
		handover(renter, image())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_OWNER_ONLY"));

		assertUnchanged();
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void handoverByOther() throws Exception {
		// given
		Member other = fixture.createMember();

		// when, then
		handover(other, image())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));

		assertUnchanged();
	}

	@Test
	@DisplayName("없는 대여면 404 RENTAL_NOT_FOUND다")
	void handoverNotFound() throws Exception {
		// when, then
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover", rental.getId() + 100)
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
	}

	@Test
	@DisplayName("사진이 없으면 400 IMAGE_COUNT_INVALID다")
	void handoverWithoutImages() throws Exception {
		// when, then
		handover(owner, json("{\"memo\": \"사진 없음\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));

		assertUnchanged();
	}

	@Test
	@DisplayName("사진이 inspection.images.max(5)장을 넘으면 400 IMAGE_COUNT_INVALID이고 사진을 저장하지 않는다")
	void handoverTooManyImages() throws Exception {
		// given
		long storedBefore = storedImageCount();

		// when, then
		handover(owner, image(), image(), image(), image(), image(), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));

		assertUnchanged();
		assertThat(storedImageCount()).isEqualTo(storedBefore);
	}

	@Test
	@DisplayName("사진 5장은 인계된다")
	void handoverMaxImages() throws Exception {
		// when, then
		handover(owner, image(), image(), image(), image(), image()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("이미지가 아닌 파일이 섞이면 400 IMAGE_INVALID_TYPE이고 앞의 사진도 저장하지 않는다")
	void handoverInvalidImage() throws Exception {
		// given
		long storedBefore = storedImageCount();
		MockMultipartFile text = new MockMultipartFile("images", "a.jpg", MediaType.IMAGE_JPEG_VALUE,
			"not an image".getBytes(StandardCharsets.UTF_8));

		// when, then
		handover(owner, image(), text)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_INVALID_TYPE"));

		assertUnchanged();
		assertThat(storedImageCount()).isEqualTo(storedBefore);
	}

	@Test
	@DisplayName("메모는 코드 포인트로 센다: 이모지 섞어 300자는 성공")
	void memoMaxLengthWithEmoji() throws Exception {
		// given: 🎒 150개 + 한글 150자 = 300자 (UTF-16으로는 450)
		String memo = "🎒".repeat(150) + "가".repeat(150);

		// when, then
		handover(owner, json("{\"memo\": \"" + memo + "\"}"), image()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("메모가 inspection.handover-memo-max(300)자를 넘으면 400 COMMON_INVALID_INPUT(memo)이다")
	void memoTooLong() throws Exception {
		// given: 이모지 섞어 301자
		String memo = "🎒".repeat(150) + "가".repeat(151);

		// when, then
		handover(owner, json("{\"memo\": \"" + memo + "\"}"), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("memo"));

		assertUnchanged();
	}

	@Test
	@DisplayName("두 번째 인계는 409 RENTAL_INVALID_TRANSITION이고 기록·이력은 1건이다")
	void handoverTwice() throws Exception {
		// given
		handover(owner, image()).andExpect(status().isOk());

		// when, then
		handover(owner, image())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));

		assertThat(inspectionRepository.findAll()).hasSize(1);
		assertThat(historyRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("취소된 대여는 409 RENTAL_INVALID_TRANSITION이다")
	void handoverCanceled() throws Exception {
		// given
		Rental canceled = rentalFixture.createRental(item, renter, RentalStatus.CANCELED, START.plusDays(10),
			START.plusDays(11));

		// when, then
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover", canceled.getId())
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
	}

	@Test
	@DisplayName("request part에 Content-Type이 없으면 500이 아니라 415 COMMON_UNSUPPORTED_MEDIA_TYPE이다")
	void requestPartWithoutContentType() throws Exception {
		// given
		MockMultipartFile request = new MockMultipartFile("request", "", null,
			"{\"memo\": \"메모\"}".getBytes(StandardCharsets.UTF_8));

		// when, then
		handover(owner, request, image())
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.error.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"));

		assertUnchanged();
	}

	@Test
	@DisplayName("request part가 text/plain이면 415 COMMON_UNSUPPORTED_MEDIA_TYPE이다")
	void requestPartTextPlain() throws Exception {
		// given
		MockMultipartFile request = new MockMultipartFile("request", "", MediaType.TEXT_PLAIN_VALUE,
			"{\"memo\": \"메모\"}".getBytes(StandardCharsets.UTF_8));

		// when, then
		handover(owner, request, image())
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.error.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("multipart가 아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE이다")
	void notMultipart() throws Exception {
		// when, then
		mockMvc.perform(patch("/api/rentals/{rentalId}/handover", rental.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"memo\": \"메모\"}")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.error.code").value("COMMON_UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("비회원이면 401이다")
	void handoverUnauthorized() throws Exception {
		// when, then
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover", rental.getId())
			.file(image()))
			.andExpect(status().isUnauthorized());
	}

	private void assertUnchanged() {
		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(found.getHandedOverAt()).isNull();
		assertThat(inspectionRepository.findAll()).isEmpty();
		assertThat(historyRepository.findAll()).isEmpty();
	}

	private ResultActions handover(Member member, MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH,
			"/api/rentals/{rentalId}/handover", rental.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}
}
