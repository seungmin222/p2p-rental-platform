package com.team7.rental.rental.controller;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.ExtensionStatus;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalExtensionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.support.FakeRentalNotificationPort.Sent;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 대여 연장 요청 (REQ-038), 결제 기능이 꺼진 기본 설정. 연장료가 있어도 결제 없이 바로 CONFIRMED라 검증·겹침 규칙을 여기서 본다.
 * 대여 11/3 ~ 11/5 RENTING, 지금 11/5 20:00, 가능 기간 11/1 ~ 11/30, 1일 10,000원.
 */
class RentalExtensionIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 20, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalExtensionRepository extensionRepository;

	@Autowired
	private RentalStatusHistoryRepository historyRepository;

	private VerifiedMember verifiedOwner;
	private Member owner;
	private Member renter;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
	}

	@Test
	@DisplayName("결제 기능이 꺼져 있으면 바로 CONFIRMED: 종료일·연장 횟수가 바뀌고, 이력(대여자, EXTENSION)과 양측 RENTAL_EXTENDED 알림이 남는다")
	void confirmsWithoutPayment() throws Exception {
		// when
		extend(renter, "2026-11-08")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.data.prevEndDate").value("2026-11-05"))
			.andExpect(jsonPath("$.data.newEndDate").value("2026-11-08"))
			.andExpect(jsonPath("$.data.additionalFee").value(30_000))
			.andExpect(jsonPath("$.data.expiresAt").doesNotExist());

		// then
		Rental extended = rentalRepository.findById(rental.getId()).orElseThrow();
		assertThat(extended.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 8));
		assertThat(extended.getExtensionCount()).isEqualTo(1);
		assertThat(extended.getStatus()).isEqualTo(RentalStatus.RENTING);
		assertThat(extensionRepository.findAll()).singleElement().satisfies(extension -> {
			assertThat(extension.getStatus()).isEqualTo(ExtensionStatus.CONFIRMED);
			assertThat(extension.getPaymentId()).isNull();
		});
		assertThat(historyRepository.findAll()).singleElement().satisfies((RentalStatusHistory history) -> {
			assertThat(history.getFromStatus()).isEqualTo(RentalStatus.RENTING);
			assertThat(history.getToStatus()).isEqualTo(RentalStatus.RENTING);
			assertThat(history.getChangedBy()).isEqualTo(renter.getId());
			assertThat(history.getReason()).isEqualTo(RentalStatusHistory.REASON_EXTENSION);
		});
		Long extensionId = extensionRepository.findAll().getFirst().getId();
		String eventId = "RENTAL_EXTENDED:" + rental.getId() + ":" + extensionId;
		assertThat(notificationPort.sent()).containsExactlyInAnyOrder(
			new Sent(renter.getId(), Type.RENTAL_EXTENDED, TargetType.RENTAL, rental.getId(), eventId),
			new Sent(owner.getId(), Type.RENTAL_EXTENDED, TargetType.RENTAL, rental.getId(), eventId));
	}

	@Test
	@DisplayName("확정된 연장이 2회면 세 번째는 400 RENTAL_EXTENSION_LIMIT_EXCEEDED")
	void countLimit() throws Exception {
		extend(renter, "2026-11-07").andExpect(status().isCreated());
		extend(renter, "2026-11-09").andExpect(status().isCreated());

		extendExpectingError(renter, "2026-11-10", 400, "RENTAL_EXTENSION_LIMIT_EXCEEDED");
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getExtensionCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("한 번에 7일까지: 8일은 400 RENTAL_EXTENSION_LIMIT_EXCEEDED, 기존 종료일 이하는 400 RENTAL_INVALID_PERIOD, 7일은 성공")
	void dayLimit() throws Exception {
		extendExpectingError(renter, "2026-11-13", 400, "RENTAL_EXTENSION_LIMIT_EXCEEDED");
		extendExpectingError(renter, "2026-11-05", 400, "RENTAL_INVALID_PERIOD");

		extend(renter, "2026-11-12").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("연체 중이면 409 RENTAL_EXTENSION_OVERDUE(반납 안내), 대여 예정이면 409 RENTAL_INVALID_TRANSITION")
	void statusGuards() throws Exception {
		// given
		Member otherRenter = fixture.createVerifiedMember().member();
		Rental overdue = rentalFixture.createRental(item, otherRenter, RentalStatus.OVERDUE,
			LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
		Rental reserved = rentalFixture.createRental(item, otherRenter, RentalStatus.RESERVED,
			LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21));

		// when & then
		extendExpectingError(otherRenter, overdue, "2026-11-04", 409, "RENTAL_EXTENSION_OVERDUE");
		extendExpectingError(otherRenter, reserved, "2026-11-22", 409, "RENTAL_INVALID_TRANSITION");
	}

	@Test
	@DisplayName("대여자 본인만: 제공자는 403 RENTAL_RENTER_ONLY, 당사자가 아니면 403 RENTAL_NOT_PARTICIPANT")
	void renterOnly() throws Exception {
		extendExpectingError(owner, "2026-11-08", 403, "RENTAL_RENTER_ONLY");
		extendExpectingError(fixture.createMember(), "2026-11-08", 403, "RENTAL_NOT_PARTICIPANT");
	}

	@Test
	@DisplayName("뒤 예약과 겹치면 409 RENTAL_EXTENSION_PERIOD_CONFLICT이고 details에 연장 가능한 최대 날짜(뒤 예약 시작 전날)를 싣는다")
	void conflictWithMaxEndDate() throws Exception {
		// given: 11/9 ~ 11/10 다른 예약
		rentalFixture.createRental(item, fixture.createVerifiedMember().member(), RentalStatus.RESERVED,
			LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10));

		// when & then: 경계 동일일(11/9)부터 겹친다
		extend(renter, "2026-11-09")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_EXTENSION_PERIOD_CONFLICT"))
			.andExpect(jsonPath("$.error.details[0].field").value("maxEndDate"))
			.andExpect(jsonPath("$.error.details[0].reason").value("2026-11-08"));
		extend(renter, "2026-11-08").andExpect(status().isCreated());
	}

	@Test
	@DisplayName("바로 다음 날부터 예약돼 있으면 409이고 details는 비어 있다(하루도 연장할 수 없음)")
	void conflictWithoutAnyDay() throws Exception {
		rentalFixture.createRental(item, fixture.createVerifiedMember().member(), RentalStatus.RESERVED,
			LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7));

		extend(renter, "2026-11-07")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RENTAL_EXTENSION_PERIOD_CONFLICT"))
			.andExpect(jsonPath("$.error.details").isEmpty());
	}

	@Test
	@DisplayName("가능 기간을 벗어나면 400 RENTAL_OUT_OF_AVAILABLE_PERIOD이고 details에 가능 기간의 끝날을 싣는다")
	void outOfAvailablePeriod() throws Exception {
		// given: 가능 기간 11/1 ~ 11/7인 물품의 대여 11/3 ~ 11/5
		Item shortItem = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7))
			.item();
		Rental shortRental = rentalFixture.createRental(shortItem, renter, RentalStatus.RENTING, START, END);

		// when & then
		mockMvc.perform(post("/api/rentals/{id}/extensions", shortRental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"newEndDate\": \"2026-11-09\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("RENTAL_OUT_OF_AVAILABLE_PERIOD"))
			.andExpect(jsonPath("$.error.details[0].field").value("maxEndDate"))
			.andExpect(jsonPath("$.error.details[0].reason").value("2026-11-07"));
	}

	@Test
	@DisplayName("없는 대여는 404, newEndDate가 없으면 400")
	void notFoundAndInvalidInput() throws Exception {
		mockMvc.perform(post("/api/rentals/{id}/extensions", rental.getId() + 999)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"newEndDate\": \"2026-11-08\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
		mockMvc.perform(post("/api/rentals/{id}/extensions", rental.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("종료일 다음 날 00:00 ~ 00:05(연체 배치 전, 상태는 RENTING)의 연장 요청도 409 RENTAL_EXTENSION_OVERDUE")
	void endDatePassedBeforeOverdueBatch() throws Exception {
		testClock.fixAt(LocalDateTime.of(2026, 11, 6, 0, 2));

		extendExpectingError(renter, "2026-11-08", 409, "RENTAL_EXTENSION_OVERDUE");
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RENTING);
		assertThat(extensionRepository.count()).isZero();
	}

	private ResultActions extend(Member member, String newEndDate) throws Exception {
		return extend(member, rental, newEndDate);
	}

	private ResultActions extend(Member member, Rental target, String newEndDate) throws Exception {
		return mockMvc.perform(post("/api/rentals/{id}/extensions", target.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"newEndDate\": \"" + newEndDate + "\"}"));
	}

	private void extendExpectingError(Member member, String newEndDate, int status, String code) throws Exception {
		extendExpectingError(member, rental, newEndDate, status, code);
	}

	private void extendExpectingError(Member member, Rental target, String newEndDate, int status, String code)
		throws Exception {
		extend(member, target, newEndDate)
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.error.code").value(code));
	}
}
