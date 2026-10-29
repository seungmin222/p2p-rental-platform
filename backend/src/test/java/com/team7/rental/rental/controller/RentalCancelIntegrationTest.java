package com.team7.rental.rental.controller;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** PATCH /api/rentals/{id}/cancel (REQ-015 단독 취소) */
class RentalCancelIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

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
		// 대여료 10,000원 × 3일 = 30,000원
		rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
	}

	@Test
	@DisplayName("대여자가 시작일 3일 전까지 취소하면 200, RENTER·수수료 0이고 이력이 남는다")
	void renterCancelFree() throws Exception {
		// when, then
		cancel(renter, "{\"reason\": \"SCHEDULE_CHANGE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelType").value("RENTER"))
			.andExpect(jsonPath("$.data.cancelReason").value("SCHEDULE_CHANGE"))
			.andExpect(jsonPath("$.data.cancelReasonText").doesNotExist())
			.andExpect(jsonPath("$.data.cancelFee").value(0))
			.andExpect(jsonPath("$.data.canceledAt").value("2026-10-28T10:00:00"));

		List<RentalStatusHistory> histories = historyRepository.findAll();
		assertThat(histories).singleElement().satisfies(history -> {
			assertThat(history.getRentalId()).isEqualTo(rental.getId());
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RESERVED);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.CANCELED);
			assertThat(history.getChangedBy()).isEqualTo(renter.getId());
			assertThat(history.getReason()).isEqualTo("SCHEDULE_CHANGE");
			assertThat(history.getChangedAt()).isEqualTo(NOW);
		});
	}

	@Test
	@DisplayName("대여자가 시작일 2일 전에 취소하면 대여료의 수수료가 cancelFee에 기록된다")
	void renterCancelWithFee() throws Exception {
		// given
		testClock.fixAt(START.minusDays(2).atTime(9, 0));

		// when, then
		cancel(renter, "{\"reason\": \"SCHEDULE_CHANGE\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cancelType").value("RENTER"))
			.andExpect(jsonPath("$.data.cancelFee").value(9_000));
	}

	@Test
	@DisplayName("제공자가 시작일 전날 취소하면 OWNER·수수료 0이고 기타 사유는 이력에 텍스트와 함께 남는다")
	void ownerCancel() throws Exception {
		// given
		testClock.fixAt(START.minusDays(1).atTime(23, 0));

		// when, then
		cancel(owner, "{\"reason\": \"OTHER\", \"reasonText\": \"  물품이 고장 났어요 \"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myRole").value("OWNER"))
			.andExpect(jsonPath("$.data.cancelType").value("OWNER"))
			.andExpect(jsonPath("$.data.cancelReason").value("OTHER"))
			.andExpect(jsonPath("$.data.cancelReasonText").value("물품이 고장 났어요"))
			.andExpect(jsonPath("$.data.cancelFee").value(0));

		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo("OTHER: 물품이 고장 났어요");
		});
	}

	@Test
	@DisplayName("시작일 당일 단독 취소는 409 RENTAL_CANCEL_NOT_ALLOWED다")
	void cancelOnStartDate() throws Exception {
		// given
		testClock.fixAt(START.atTime(0, 0));

		// when, then
		cancel(renter, "{\"reason\": \"SCHEDULE_CHANGE\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_NOT_ALLOWED"));
		assertThat(historyRepository.count()).isZero();
	}

	@Test
	@DisplayName("이미 취소된 대여를 다시 취소하면 409 RENTAL_INVALID_TRANSITION이다")
	void cancelTwice() throws Exception {
		// given
		cancel(renter, "{\"reason\": \"SCHEDULE_CHANGE\"}").andExpect(status().isOk());

		// when, then
		cancel(owner, "{\"reason\": \"SCHEDULE_CHANGE\"}")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
		assertThat(historyRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("사유가 OTHER인데 텍스트가 없거나 공백이면 400, 필드는 reasonText다")
	void otherWithoutText() throws Exception {
		for (String body : List.of("{\"reason\": \"OTHER\"}", "{\"reason\": \"OTHER\", \"reasonText\": \"   \"}")) {
			cancel(renter, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
				.andExpect(jsonPath("$.error.details[0].field").value("reasonText"));
		}
	}

	@Test
	@DisplayName("사유 텍스트가 200자를 넘으면 400, 200자는 허용한다")
	void reasonTextLength() throws Exception {
		cancel(renter, "{\"reason\": \"OTHER\", \"reasonText\": \"" + "가".repeat(201) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("reasonText"));

		cancel(renter, "{\"reason\": \"OTHER\", \"reasonText\": \"" + "가".repeat(200) + "\"}")
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("사유 텍스트 길이는 문자(코드 포인트) 기준이라 이모지가 섞인 200자는 허용하고 201자는 400이다")
	void reasonTextLengthWithEmoji() throws Exception {
		String text200 = "😀".repeat(100) + "가".repeat(100);

		cancel(renter, "{\"reason\": \"OTHER\", \"reasonText\": \"" + text200 + "가\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("reasonText"));

		cancel(renter, "{\"reason\": \"OTHER\", \"reasonText\": \"" + text200 + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cancelReasonText").value(text200));
	}

	@Test
	@DisplayName("사유가 없으면 400, 필드는 reason이다")
	void reasonMissing() throws Exception {
		cancel(renter, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("reason"));
	}

	@Test
	@DisplayName("당사자가 아니면 403 RENTAL_NOT_PARTICIPANT다")
	void notParticipant() throws Exception {
		cancel(fixture.createMember(), "{\"reason\": \"SCHEDULE_CHANGE\"}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("없는 대여는 404 RENTAL_NOT_FOUND다")
	void notFound() throws Exception {
		mockMvc.perform(patch("/api/rentals/{id}/cancel", rental.getId() + 999)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"SCHEDULE_CHANGE\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
	}

	@Test
	@DisplayName("토큰이 없으면 401 AUTH_UNAUTHORIZED다")
	void unauthorized() throws Exception {
		mockMvc.perform(patch("/api/rentals/{id}/cancel", rental.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\": \"SCHEDULE_CHANGE\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("취소하면 기간 점유가 풀려 같은 기간을 다시 예약할 수 있다")
	void rebookAfterCancel() throws Exception {
		// given
		Member another = fixture.createVerifiedMember().member();
		String body = "{\"itemId\": " + item.getId() + ", \"startDate\": \"" + START + "\", \"endDate\": \"" + END
			+ "\"}";
		reserve(another, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_PERIOD_CONFLICT"));

		// when
		cancel(renter, "{\"reason\": \"SCHEDULE_CHANGE\"}").andExpect(status().isOk());

		// then
		reserve(another, body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("RESERVED"));
	}

	private ResultActions cancel(Member member, String body) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions reserve(Member member, String body) throws Exception {
		return mockMvc.perform(post("/api/rentals")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}
}
