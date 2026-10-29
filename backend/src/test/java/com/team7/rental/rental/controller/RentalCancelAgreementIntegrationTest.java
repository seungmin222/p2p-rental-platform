package com.team7.rental.rental.controller;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.CancelAgreementStatus;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalCancelAgreementRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** POST·PATCH /api/rentals/{id}/cancel-agreements (REQ-015 양측 동의 취소) */
class RentalCancelAgreementIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	// 시작일 당일, 인계 전
	private static final LocalDateTime NOW = START.atTime(10, 0);
	// cancel-agreement.expire-hours(48)
	private static final LocalDateTime EXPIRES_AT = NOW.plusHours(48);
	private static final String REQUEST_BODY = "{\"reason\": \"OTHER\", \"reasonText\": \"갑자기 출장이 잡혔어요\"}";

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalCancelAgreementRepository agreementRepository;

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
	@DisplayName("시작일 이후 요청하면 201 REQUESTED이고, 상대의 상세에 진행 중인 요청이 보인다")
	void request() throws Exception {
		// when, then
		requestAgreement(renter, REQUEST_BODY)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.agreementId").isNumber())
			.andExpect(jsonPath("$.data.rentalId").value(rental.getId()))
			.andExpect(jsonPath("$.data.status").value("REQUESTED"))
			.andExpect(jsonPath("$.data.requestedByMe").value(true))
			.andExpect(jsonPath("$.data.reason").value("OTHER"))
			.andExpect(jsonPath("$.data.reasonText").value("갑자기 출장이 잡혔어요"))
			.andExpect(jsonPath("$.data.expiresAt").value("2026-11-05T10:00:00"));

		mockMvc.perform(get("/api/rentals/{id}", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESERVED"))
			.andExpect(jsonPath("$.data.activeCancelAgreement.requestedByMe").value(false))
			.andExpect(jsonPath("$.data.activeCancelAgreement.reason").value("OTHER"))
			.andExpect(jsonPath("$.data.activeCancelAgreement.expiresAt").value("2026-11-05T10:00:00"));
	}

	@Test
	@DisplayName("수락하면 MUTUAL·수수료 0으로 취소되고, 같은 트랜잭션에서 만든 응답의 activeCancelAgreement는 null이다")
	void accept() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);
		testClock.advance(Duration.ofHours(1));

		// when, then
		respond(owner, agreementId, "ACCEPT")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelType").value("MUTUAL"))
			.andExpect(jsonPath("$.data.cancelFee").value(0))
			.andExpect(jsonPath("$.data.cancelReason").value("OTHER"))
			.andExpect(jsonPath("$.data.cancelReasonText").value("갑자기 출장이 잡혔어요"))
			.andExpect(jsonPath("$.data.activeCancelAgreement").doesNotExist());

		// canceledBy는 요청자, 이력 changedBy는 수락자
		LocalDateTime respondedAt = NOW.plusHours(1);
		Rental canceled = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(canceled.getCanceledBy()).isEqualTo(renter.getId());
		assertThat(canceled.getCancelType()).isEqualTo(CancelType.MUTUAL);
		assertThat(canceled.getCanceledAt()).isEqualTo(respondedAt);
		assertThat(historyRepository.findAll()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RESERVED);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.CANCELED);
			assertThat(history.getChangedBy()).isEqualTo(owner.getId());
			assertThat(history.getReason()).isEqualTo("OTHER: 갑자기 출장이 잡혔어요");
		});
		RentalCancelAgreement agreement = agreementRepository.findById(agreementId).orElseThrow();
		assertThat(agreement.getStatus()).isEqualTo(CancelAgreementStatus.ACCEPTED);
		assertThat(agreement.getRespondedAt()).isEqualTo(respondedAt);
	}

	@Test
	@DisplayName("거절하면 대여는 그대로이고 요청만 종료되며, 그 뒤 새 요청을 보낼 수 있다")
	void reject() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);

		// when, then
		respond(owner, agreementId, "REJECT")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESERVED"))
			.andExpect(jsonPath("$.data.canceledAt").doesNotExist())
			.andExpect(jsonPath("$.data.activeCancelAgreement").doesNotExist());

		RentalCancelAgreement agreement = agreementRepository.findById(agreementId).orElseThrow();
		assertThat(agreement.getStatus()).isEqualTo(CancelAgreementStatus.REJECTED);
		assertThat(agreement.getRespondedAt()).isEqualTo(NOW);
		assertThat(historyRepository.count()).isZero();

		requestAgreement(owner, "{\"reason\": \"ITEM_PROBLEM\"}").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("진행 중인 요청이 있으면 어느 쪽이든 다시 요청할 때 409 IN_PROGRESS다")
	void requestInProgress() throws Exception {
		// given
		requestAgreement(renter, REQUEST_BODY).andExpect(status().isCreated());

		// when, then
		requestAgreement(renter, REQUEST_BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_IN_PROGRESS"));
		requestAgreement(owner, REQUEST_BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_IN_PROGRESS"));
		assertThat(agreementRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("시작일 전에 요청하면 409 TOO_EARLY다(단독 취소 대상)")
	void requestBeforeStart() throws Exception {
		// given
		testClock.fixAt(START.minusDays(1).atTime(23, 59));

		// when, then
		requestAgreement(renter, REQUEST_BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_TOO_EARLY"));
	}

	@Test
	@DisplayName("대여 예정이 아니면 요청할 때 409 RENTAL_INVALID_TRANSITION이다")
	void requestNotReserved() throws Exception {
		// given
		Rental renting = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START.plusDays(10),
			START.plusDays(11));
		testClock.fixAt(START.plusDays(10).atTime(10, 0));

		// when, then
		mockMvc.perform(post("/api/rentals/{id}/cancel-agreements", renting.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(REQUEST_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_INVALID_TRANSITION"));
	}

	@Test
	@DisplayName("요청 사유가 OTHER인데 텍스트가 없으면 400, 필드는 reasonText다")
	void requestWithoutReasonText() throws Exception {
		requestAgreement(renter, "{\"reason\": \"OTHER\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("reasonText"));
	}

	@Test
	@DisplayName("당사자가 아니면 요청·응답 모두 403 RENTAL_NOT_PARTICIPANT다")
	void notParticipant() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);
		Member stranger = fixture.createMember();

		// when, then
		requestAgreement(stranger, REQUEST_BODY)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
		respond(stranger, agreementId, "ACCEPT")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("요청자 본인이 응답하면 403 SELF_RESPONSE다")
	void selfResponse() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);

		// when, then
		respond(renter, agreementId, "ACCEPT")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE"));
	}

	@Test
	@DisplayName("다른 대여의 agreementId로 응답하면 404 CANCEL_AGREEMENT_NOT_FOUND다")
	void agreementOfAnotherRental() throws Exception {
		// given: 같은 두 회원의 다른 대여에 걸린 요청
		Rental another = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, LocalDate.of(2026, 11, 1),
			LocalDate.of(2026, 11, 2));
		Long otherAgreementId = agreementRepository.save(RentalCancelAgreement.request(another.getId(),
			renter.getId(), CancelReason.SCHEDULE_CHANGE, null, EXPIRES_AT)).getId();

		// when, then
		respond(owner, otherAgreementId, "ACCEPT")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_NOT_FOUND"));
		assertThat(rentalRepository.findById(another.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("없는 대여는 404 RENTAL_NOT_FOUND, 토큰이 없으면 401이다")
	void notFoundAndUnauthorized() throws Exception {
		mockMvc.perform(post("/api/rentals/{id}/cancel-agreements", rental.getId() + 999)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(REQUEST_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
		mockMvc.perform(patch("/api/rentals/{id}/cancel-agreements/{agreementId}", rental.getId(), 1)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"action\": \"ACCEPT\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	@DisplayName("이미 응답한 요청에 다시 응답하면 409 CLOSED다")
	void alreadyResponded() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);
		respond(owner, agreementId, "REJECT").andExpect(status().isOk());

		// when, then
		respond(owner, agreementId, "ACCEPT")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_CLOSED"));
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RESERVED);
	}

	@Test
	@DisplayName("정확히 만료 시각에 응답하면 409 CLOSED, 1초 전에는 수락된다")
	void respondAtExpiry() throws Exception {
		// given
		Long agreementId = requestAndGetId(renter);
		testClock.fixAt(EXPIRES_AT);

		// when, then
		respond(owner, agreementId, "ACCEPT")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_CANCEL_AGREEMENT_CLOSED"));

		testClock.fixAt(EXPIRES_AT.minusSeconds(1));
		respond(owner, agreementId, "ACCEPT")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.cancelType").value("MUTUAL"));
	}

	@Test
	@DisplayName("만료 시각이 지났지만 배치가 아직 돌지 않은 REQUESTED 행이 있어도 새 요청은 201이다")
	void requestAfterExpiryBeforeBatch() throws Exception {
		// given
		Long expiredId = requestAndGetId(renter);
		testClock.fixAt(EXPIRES_AT);
		mockMvc.perform(get("/api/rentals/{id}", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.activeCancelAgreement").doesNotExist());

		// when, then
		requestAgreement(owner, REQUEST_BODY)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.expiresAt").value("2026-11-07T10:00:00"));
		assertThat(agreementRepository.findById(expiredId).orElseThrow().getStatus())
			.isEqualTo(CancelAgreementStatus.REQUESTED);
		mockMvc.perform(get("/api/rentals/{id}", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(jsonPath("$.data.activeCancelAgreement.requestedByMe").value(false));
	}

	private Long requestAndGetId(Member member) throws Exception {
		String json = requestAgreement(member, REQUEST_BODY)
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number)JsonPath.read(json, "$.data.agreementId")).longValue();
	}

	private ResultActions requestAgreement(Member member, String body) throws Exception {
		return mockMvc.perform(post("/api/rentals/{id}/cancel-agreements", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions respond(Member member, Long agreementId, String action) throws Exception {
		return mockMvc.perform(patch("/api/rentals/{id}/cancel-agreements/{agreementId}", rental.getId(), agreementId)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"action\": \"" + action + "\"}"));
	}
}
