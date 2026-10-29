package com.team7.rental.rental.controller;

import static com.team7.rental.support.InspectionParts.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** GET /api/rentals/{id}/inspections (REQ-017·018). 기록은 실제 API로 만든다 */
class RentalInspectionQueryIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime HANDOVER_AT = START.atTime(9, 0);
	private static final LocalDateTime RETURN_AT = END.atTime(18, 0);
	private static final String ONE_FALSE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": false,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}, "note": "얼룩"}""";

	private Member owner;
	private Member renter;
	private Rental rental;

	@BeforeEach
	void setUp() throws Exception {
		testClock.fixAt(HANDOVER_AT);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover", rental.getId())
			.file(json("{\"memo\": \"충전기 포함\"}"))
			.file(image())
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("인계만 있으면 HANDOVER 기록 1건이고, 대여자도 볼 수 있다")
	void handoverOnly() throws Exception {
		// when, then
		getInspections(renter)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].phase").value("HANDOVER"))
			.andExpect(jsonPath("$.data[0].imageUrls", hasSize(2)))
			.andExpect(jsonPath("$.data[0].imageUrls[0]", startsWith("/files/inspections/")))
			.andExpect(jsonPath("$.data[0].note").value("충전기 포함"))
			.andExpect(jsonPath("$.data[0].checklist").doesNotExist())
			.andExpect(jsonPath("$.data[0].writerRole").value("OWNER"))
			.andExpect(jsonPath("$.data[0].createdAt").value("2026-11-03T09:00:00"))
			.andExpect(jsonPath("$.data[0].result").doesNotExist())
			.andExpect(jsonPath("$.data[0].resultImageUrls", hasSize(0)))
			.andExpect(jsonPath("$.data[0].aiResult").doesNotExist());
	}

	@Test
	@DisplayName("인계·반납·판정이 모두 있으면 HANDOVER → RETURN 순이고 체크리스트·판정이 담긴다")
	void handoverAndReturn() throws Exception {
		// given
		testClock.fixAt(RETURN_AT);
		mockMvc.perform(multipart("/api/rentals/{rentalId}/return-request", rental.getId())
			.file(json(ONE_FALSE))
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isOk());
		testClock.fixAt(RETURN_AT.plusHours(2));
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", rental.getId())
			.file(json("{\"result\": \"ABNORMAL\", \"resultNote\": \"얼룩이 지워지지 않아요\"}"))
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());

		// when, then
		getInspections(owner)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[0].phase").value("HANDOVER"))
			.andExpect(jsonPath("$.data[1].phase").value("RETURN"))
			.andExpect(jsonPath("$.data[1].writerRole").value("RENTER"))
			.andExpect(jsonPath("$.data[1].note").value("얼룩"))
			.andExpect(jsonPath("$.data[1].checklist.NO_CONTAMINATION").value(false))
			.andExpect(jsonPath("$.data[1].checklist.NO_EXTERIOR_DAMAGE").value(true))
			.andExpect(jsonPath("$.data[1].checklist.*", hasSize(5)))
			.andExpect(jsonPath("$.data[1].createdAt").value("2026-11-05T18:00:00"))
			.andExpect(jsonPath("$.data[1].result").value("ABNORMAL"))
			.andExpect(jsonPath("$.data[1].resultNote").value("얼룩이 지워지지 않아요"))
			.andExpect(jsonPath("$.data[1].resultImageUrls", hasSize(1)));
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void notParticipant() throws Exception {
		// when, then
		getInspections(fixture.createMember())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("없는 대여면 404 RENTAL_NOT_FOUND다")
	void notFound() throws Exception {
		// when, then
		mockMvc.perform(get("/api/rentals/{rentalId}/inspections", rental.getId() + 999)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
	}

	private ResultActions getInspections(Member member) throws Exception {
		return mockMvc.perform(get("/api/rentals/{rentalId}/inspections", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId())));
	}
}
