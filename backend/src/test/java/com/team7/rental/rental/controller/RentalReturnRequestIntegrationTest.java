package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.entity.ReturnChecklistItem;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** POST /api/rentals/{id}/return-request (REQ-018 반납 요청·제공자 대리 접수) */
class RentalReturnRequestIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = END.atTime(18, 0);
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";
	private static final String ONE_FALSE_CHECKLIST = """
		"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": false,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}""";

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
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
	}

	@Test
	@DisplayName("대여자가 사진·체크리스트로 반납 요청하면 200 RETURNED이고 반납 시각·기록·이력이 남으며 연체료는 그대로다")
	void requestReturn() throws Exception {
		// when, then
		requestReturn(rental, renter, json(ALL_TRUE), image(), image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.returnedAt").value("2026-11-05T18:00:00"));

		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getReturnedAt()).isEqualTo(NOW);
		assertThat(found.getOverdueFee()).isZero();
		assertThat(inspectionRepository.findAll()).singleElement().satisfies(inspection -> {
			assertThat(inspection.getPhase()).isEqualTo(InspectionPhase.RETURN);
			assertThat(inspection.getWrittenBy()).isEqualTo(renter.getId());
			assertThat(inspection.getImageUrls()).hasSize(2);
			assertThat(inspection.getChecklist()).hasSize(ReturnChecklistItem.values().length)
				.doesNotContainValue(false);
			assertThat(inspection.getNote()).isNull();
			assertThat(inspection.isLostReported()).isFalse();
			assertThat(inspection.getResult()).isNull();
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RENTING);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RETURNED);
			assertThat(history.getChangedBy()).isEqualTo(renter.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_RETURN_REQUEST);
		});
	}

	@Test
	@DisplayName("제공자가 대리 접수하면 기록 작성자와 이력 changedBy가 제공자다")
	void proxyReturnByOwner() throws Exception {
		// when, then
		requestReturn(rental, owner, json(ALL_TRUE), image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"))
			.andExpect(jsonPath("$.data.myRole").value("OWNER"));

		assertThat(inspectionRepository.findAll()).singleElement()
			.satisfies(inspection -> assertThat(inspection.getWrittenBy()).isEqualTo(owner.getId()));
		assertThat(historyRepository.findAll()).singleElement()
			.satisfies(history -> assertThat(history.getChangedBy()).isEqualTo(owner.getId()));
	}

	@Test
	@DisplayName("연체 중에도 반납 요청할 수 있다")
	void requestReturnOverdue() throws Exception {
		// given
		Rental overdue = rentalFixture.createRental(item, renter, RentalStatus.OVERDUE, START.plusDays(10),
			START.plusDays(11));

		// when, then
		requestReturn(overdue, renter, json(ALL_TRUE), image())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"));

		assertThat(historyRepository.findAll()).singleElement()
			.satisfies(history -> assertThat(history.getFromStatus()).isEqualTo(RentalStatus.OVERDUE));
	}

	@Test
	@DisplayName("false 항목이 있으면 메모와 함께 반납되고 메모는 앞뒤 공백을 지워 저장된다")
	void requestReturnWithUncheckedItem() throws Exception {
		// when, then
		requestReturn(rental, renter, json("{" + ONE_FALSE_CHECKLIST + ", \"note\": \"  옆면 얼룩 \"}"), image())
			.andExpect(status().isOk());

		assertThat(inspectionRepository.findAll()).singleElement().satisfies(inspection -> {
			assertThat(inspection.getChecklist()).containsEntry(ReturnChecklistItem.NO_CONTAMINATION, false);
			assertThat(inspection.getNote()).isEqualTo("옆면 얼룩");
		});
	}

	@Test
	@DisplayName("false 항목이 있는데 메모가 없거나 공백뿐이면 400 COMMON_INVALID_INPUT(note)이다")
	void uncheckedItemWithoutNote() throws Exception {
		for (String body : new String[] {"{" + ONE_FALSE_CHECKLIST + "}",
			"{" + ONE_FALSE_CHECKLIST + ", \"note\": \"   \"}"}) {
			// when, then
			requestReturn(rental, renter, json(body), image())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
				.andExpect(jsonPath("$.error.details[0].field").value("note"));
		}

		assertUnchanged();
	}

	@Test
	@DisplayName("메모가 return.note-max(300)자를 넘으면 400(note)이고, 이모지 섞어 300자는 성공한다")
	void noteMaxLength() throws Exception {
		// given
		String tooLong = "🧽".repeat(100) + "가".repeat(201);
		String max = "🧽".repeat(100) + "가".repeat(200);

		// when, then
		requestReturn(rental, renter, json("{" + ONE_FALSE_CHECKLIST + ", \"note\": \"" + tooLong + "\"}"), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("note"));
		requestReturn(rental, renter, json("{" + ONE_FALSE_CHECKLIST + ", \"note\": \"" + max + "\"}"), image())
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("체크리스트 항목이 빠지면 400 COMMON_INVALID_INPUT(checklist)이다")
	void checklistMissingItem() throws Exception {
		// given: CONSUMABLES_RESTORED 없음
		String body = """
			{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
			"WORKS_NORMALLY": true}}""";

		// when, then
		requestReturn(rental, renter, json(body), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("checklist"));

		assertUnchanged();
	}

	@Test
	@DisplayName("체크리스트 값이 null이면 400(checklist)이다")
	void checklistNullValue() throws Exception {
		// given
		String body = """
			{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": null,
			"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";

		// when, then
		requestReturn(rental, renter, json(body), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("checklist"));
	}

	@Test
	@DisplayName("체크리스트가 없으면 400(checklist)이다")
	void checklistAbsent() throws Exception {
		// when, then
		requestReturn(rental, renter, json("{\"note\": \"사진만\"}"), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("checklist"));
	}

	@Test
	@DisplayName("체크리스트에 모르는 키가 있으면 JSON 변환 단계(HttpMessageNotReadable 핸들러)에서 400 COMMON_INVALID_INPUT이다")
	void checklistUnknownKey() throws Exception {
		// given
		String body = """
			{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
			"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true, "SMELLS_GOOD": true}}""";

		// when, then: "형식이 올바르지 않습니다."는 HttpMessageNotReadable 핸들러의 문구다(검증기 문구와 다름)
		requestReturn(rental, renter, json(body), image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("checklist"))
			.andExpect(jsonPath("$.error.details[0].reason").value("형식이 올바르지 않습니다."));

		assertUnchanged();
	}

	@Test
	@DisplayName("request part가 없으면 400 COMMON_INVALID_INPUT(request)이다")
	void requestPartMissing() throws Exception {
		// when, then
		requestReturn(rental, renter, image())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("request"));
	}

	@Test
	@DisplayName("사진 없이 체크리스트만 보내면 400 IMAGE_COUNT_INVALID다")
	void requestReturnWithoutImages() throws Exception {
		// when, then
		requestReturn(rental, renter, json(ALL_TRUE))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));

		assertUnchanged();
	}

	@Test
	@DisplayName("대여 예정에서 반납 요청하면 409 RENTAL_INVALID_TRANSITION이다")
	void requestReturnReserved() throws Exception {
		// given
		Rental reserved = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START.plusDays(10),
			START.plusDays(11));

		// when, then
		requestReturn(reserved, renter, json(ALL_TRUE), image())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));

		assertThat(inspectionRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("두 번째 반납 요청은 409 RENTAL_INVALID_TRANSITION이다")
	void requestReturnTwice() throws Exception {
		// given
		requestReturn(rental, renter, json(ALL_TRUE), image()).andExpect(status().isOk());

		// when, then
		requestReturn(rental, owner, json(ALL_TRUE), image())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));

		assertThat(inspectionRepository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void requestReturnByOther() throws Exception {
		// when, then
		requestReturn(rental, fixture.createMember(), json(ALL_TRUE), image())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));

		assertUnchanged();
	}

	private void assertUnchanged() {
		Rental found = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(RentalStatus.RENTING);
		assertThat(found.getReturnedAt()).isNull();
		assertThat(inspectionRepository.findAll()).isEmpty();
		assertThat(historyRepository.findAll()).isEmpty();
	}

	private ResultActions requestReturn(Rental target, Member member, MockMultipartFile... parts) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/rentals/{rentalId}/return-request",
			target.getId());
		for (MockMultipartFile part : parts) {
			request.file(part);
		}
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}
}
