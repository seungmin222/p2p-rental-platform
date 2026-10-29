package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.InspectionResult;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** PATCH /api/rentals/{id}/return (REQ-018 제공자 상태 확인, 1순위는 판정 기록 후 COMPLETED) */
class RentalReturnConfirmIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final LocalDateTime NOW = RETURNED_AT.plusDays(1);
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";
	private static final String ABNORMAL = "{\"result\": \"ABNORMAL\", \"resultNote\": \"  모서리 파손 \"}";

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
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
	}

	@Test
	@DisplayName("정상 판정이면 200 COMPLETED이고 완료 시각·판정·이력이 남는다")
	void confirmNormal() throws Exception {
		// when, then
		confirm(owner, json(NORMAL))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))
			.andExpect(jsonPath("$.data.completedAt").value("2026-11-06T18:00:00"));

		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getCompletedAt()).isEqualTo(NOW);
		assertThat(returnInspection()).satisfies(inspection -> {
			assertThat(inspection.getResult()).isEqualTo(InspectionResult.NORMAL);
			assertThat(inspection.getResultNote()).isNull();
			assertThat(inspection.getResultImageUrls()).isNull();
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RETURNED);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.COMPLETED);
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_CONFIRM_NORMAL);
			assertThat(history.getChangedAt()).isEqualTo(NOW);
		});
	}

	@Test
	@DisplayName("정상 판정에도 내용을 남길 수 있다")
	void confirmNormalWithNote() throws Exception {
		// when, then
		confirm(owner, json("{\"result\": \"NORMAL\", \"resultNote\": \"깨끗하게 썼어요\"}"))
			.andExpect(status().isOk());

		assertThat(returnInspection().getResultNote()).isEqualTo("깨끗하게 썼어요");
	}

	@Test
	@DisplayName("이상 판정(내용·사진)이면 1순위는 COMPLETED이고 판정 내용·사진이 기록된다")
	void confirmAbnormal() throws Exception {
		// when, then
		confirm(owner, json(ABNORMAL), image(), image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("COMPLETED"));

		assertThat(returnInspection()).satisfies(inspection -> {
			assertThat(inspection.getResult()).isEqualTo(InspectionResult.ABNORMAL);
			assertThat(inspection.getResultNote()).isEqualTo("모서리 파손");
			assertThat(inspection.getResultImageUrls()).hasSize(2)
				.allSatisfy(url -> assertThat(url).startsWith("/files/inspections/"));
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.COMPLETED);
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_CONFIRM_ABNORMAL);
		});
	}

	@Test
	@DisplayName("이상 판정에 내용이 없거나 공백뿐이면 400 COMMON_INVALID_INPUT(resultNote)이다")
	void abnormalWithoutNote() throws Exception {
		for (String body : new String[] {"{\"result\": \"ABNORMAL\"}",
			"{\"result\": \"ABNORMAL\", \"resultNote\": \"  \"}"}) {
			// when, then
			confirm(owner, json(body), image())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
				.andExpect(jsonPath("$.error.details[0].field").value("resultNote"));
		}

		assertUnchanged();
	}

	@Test
	@DisplayName("이상 판정에 사진이 없으면 400 IMAGE_COUNT_INVALID다")
	void abnormalWithoutImages() throws Exception {
		// when, then
		confirm(owner, json(ABNORMAL))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));

		assertUnchanged();
	}

	@Test
	@DisplayName("이상 내용이 inspection.abnormal-note-max(500)자를 넘으면 400(resultNote)이다")
	void abnormalNoteTooLong() throws Exception {
		// given: 이모지 섞어 501자
		String note = "💥".repeat(250) + "가".repeat(251);

		// when, then
		confirm(owner, json("{\"result\": \"ABNORMAL\", \"resultNote\": \"" + note + "\"}"), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("resultNote"));

		assertUnchanged();
	}

	@Test
	@DisplayName("정상 판정에 사진을 보내면 400 IMAGE_COUNT_INVALID이고 사진을 저장하지 않는다")
	void normalWithImages() throws Exception {
		// given
		long storedBefore = storedImageCount();

		// when, then
		confirm(owner, json(NORMAL), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));

		assertUnchanged();
		assertThat(storedImageCount()).isEqualTo(storedBefore);
	}

	@Test
	@DisplayName("판정 값이 없으면 400(result)이다")
	void resultAbsent() throws Exception {
		// when, then
		confirm(owner, json("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("result"));
	}

	@Test
	@DisplayName("대여자가 상태 확인하면 403 RENTAL_OWNER_ONLY다")
	void confirmByRenter() throws Exception {
		// when, then
		confirm(renter, json(NORMAL))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_OWNER_ONLY"));

		assertUnchanged();
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void confirmByOther() throws Exception {
		// when, then
		confirm(fixture.createMember(), json(NORMAL))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("반납 완료인데 이미 판정된 기록이면 409 RENTAL_INVALID_TRANSITION이고 첫 판정이 남는다")
	void alreadyJudged() throws Exception {
		// given: REQ-049 이후 생길 '이상 → RETURNED 유지' 상태를 직접 만든다
		ItemInspection inspection = returnInspection();
		inspection.judge(InspectionResult.ABNORMAL, "파손", List.of("/files/inspections/x.jpg"));
		inspectionRepository.save(inspection);

		// when, then
		confirm(owner, json(NORMAL))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));

		assertThat(returnInspection().getResult()).isEqualTo(InspectionResult.ABNORMAL);
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RETURNED);
	}

	@Test
	@DisplayName("거래 완료 뒤 다시 상태 확인하면 409 RENTAL_INVALID_TRANSITION이다")
	void confirmTwice() throws Exception {
		// given
		confirm(owner, json(NORMAL)).andExpect(status().isOk());

		// when, then
		confirm(owner, json(ABNORMAL), image())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));

		assertThat(historyRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("대여 중에 상태 확인하면 409 RENTAL_INVALID_TRANSITION이다")
	void confirmRenting() throws Exception {
		// given
		Rental renting = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START.plusDays(10),
			START.plusDays(11));

		// when, then
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", renting.getId())
			.file(json(NORMAL))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
	}

	private ItemInspection returnInspection() {
		return inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN).orElseThrow();
	}

	private void assertUnchanged() {
		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(RentalStatus.RETURNED);
		assertThat(found.getCompletedAt()).isNull();
		assertThat(returnInspection().isJudged()).isFalse();
		assertThat(historyRepository.findAll()).isEmpty();
	}

	private ResultActions confirm(Member member, MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return",
			rental.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}
}
