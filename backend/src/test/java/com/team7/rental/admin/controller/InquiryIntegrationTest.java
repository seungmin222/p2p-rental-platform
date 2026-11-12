package com.team7.rental.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 문의·분쟁 접수와 내 문의 목록 (REQ-063): 검증·권한·에러 코드. 정산 보류는 DisputeSettlementHoldIntegrationTest */
class InquiryIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);

	private Member owner;
	private Member renter;
	private Member stranger;
	private Item item;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		stranger = fixture.createVerifiedMember().member();
		item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
	}

	@Test
	@DisplayName("일반 문의는 대여 없이 접수되고 RECEIVED로 시작한다")
	void generalWithoutRental() throws Exception {
		create(stranger, body("GENERAL", null, "결제 수단을 바꾸고 싶어요.")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.inquiryId").exists())
			.andExpect(jsonPath("$.data.type").value("GENERAL"))
			.andExpect(jsonPath("$.data.rentalId").doesNotExist())
			.andExpect(jsonPath("$.data.content").value("결제 수단을 바꾸고 싶어요."))
			.andExpect(jsonPath("$.data.status").value("RECEIVED"))
			.andExpect(jsonPath("$.data.createdAt").exists());
	}

	@Test
	@DisplayName("일반 문의에 대여를 연결하면 당사자만 접수할 수 있다(비당사자 403)")
	void generalWithRental() throws Exception {
		create(renter, body("GENERAL", rental.getId(), "인계 장소 문의")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.rentalId").value(rental.getId()));
		create(stranger, body("GENERAL", rental.getId(), "인계 장소 문의")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
	}

	@Test
	@DisplayName("분쟁은 rentalId가 없으면 400(details rentalId)이다")
	void disputeWithoutRental() throws Exception {
		create(renter, body("DISPUTE", null, "보증금 차감에 이의가 있습니다.")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("rentalId"));
	}

	@Test
	@DisplayName("분쟁은 비당사자 403, 없는 대여 404")
	void disputeParticipantAndNotFound() throws Exception {
		create(stranger, body("DISPUTE", rental.getId(), "이의 있음")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_PARTICIPANT"));
		create(renter, body("DISPUTE", rental.getId() + 1_000, "이의 있음")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RENTAL_NOT_FOUND"));
	}

	@Test
	@DisplayName("결제 대기(PENDING_PAYMENT) 대여에는 분쟁을 접수할 수 없다(409)")
	void disputePendingPayment() throws Exception {
		Rental pending = rentalFixture.createPendingPayment(item, renter, END.plusDays(10), END.plusDays(12),
			NOW.plusMinutes(10));

		create(renter, body("DISPUTE", pending.getId(), "이의 있음")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("INQUIRY_DISPUTE_NOT_ALLOWED"));
	}

	@Test
	@DisplayName("같은 회원·대여의 분쟁 중복은 409이고, 상대방 당사자는 따로 접수할 수 있다")
	void disputeDuplicate() throws Exception {
		create(renter, body("DISPUTE", rental.getId(), "첫 번째 이의")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.type").value("DISPUTE"))
			.andExpect(jsonPath("$.data.status").value("RECEIVED"));

		create(renter, body("DISPUTE", rental.getId(), "두 번째 이의")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("INQUIRY_DISPUTE_ALREADY_OPEN"));
		create(owner, body("DISPUTE", rental.getId(), "제공자 측 이의")).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("취소된(CANCELED) 대여에도 분쟁을 접수할 수 있다")
	void disputeCanceled() throws Exception {
		Rental canceled = rentalFixture.createRental(item, renter, RentalStatus.CANCELED, END.plusDays(10),
			END.plusDays(12));

		create(renter, body("DISPUTE", canceled.getId(), "취소 수수료 이의")).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("내용은 code point 기준 1,000자까지 통과하고 1,001자는 400(details content), 공백만은 400")
	void contentLength() throws Exception {
		create(renter, body("GENERAL", null, "가".repeat(1_000))).andExpect(status().isCreated());
		// 이모지는 UTF-16 두 글자지만 1자로 센다
		create(renter, body("GENERAL", null, "😀".repeat(1_000))).andExpect(status().isCreated());

		create(renter, body("GENERAL", null, "가".repeat(1_001))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("content"));
		create(renter, body("GENERAL", null, "   ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	@Test
	@DisplayName("type이 없으면 400, 로그인하지 않으면 401")
	void invalidRequest() throws Exception {
		create(renter, "{\"content\": \"문의\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		mockMvc.perform(post("/api/inquiries").contentType(MediaType.APPLICATION_JSON)
			.content(body("GENERAL", null, "문의"))).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("내 문의 목록은 본인 것만 최신 접수 순으로 처리 상태와 함께 준다")
	void myInquiries() throws Exception {
		create(renter, body("GENERAL", null, "첫 문의")).andExpect(status().isCreated());
		create(renter, body("DISPUTE", rental.getId(), "두 번째 분쟁")).andExpect(status().isCreated());
		create(owner, body("GENERAL", null, "다른 회원 문의")).andExpect(status().isCreated());

		list(renter, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.items[0].content").value("두 번째 분쟁"))
			.andExpect(jsonPath("$.data.items[0].type").value("DISPUTE"))
			.andExpect(jsonPath("$.data.items[0].rentalId").value(rental.getId()))
			.andExpect(jsonPath("$.data.items[0].status").value("RECEIVED"))
			.andExpect(jsonPath("$.data.items[1].content").value("첫 문의"));
	}

	@Test
	@DisplayName("목록 페이지 크기는 inquiry.page(20) 고정이고, page가 음수면 400")
	void myInquiriesPaging() throws Exception {
		for (int i = 0; i < 21; i++) {
			create(renter, body("GENERAL", null, "문의 " + i)).andExpect(status().isCreated());
		}

		list(renter, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(20))
			.andExpect(jsonPath("$.data.items.length()").value(20))
			.andExpect(jsonPath("$.data.hasNext").value(true));
		list(renter, "1").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].content").value("문의 0"));
		list(renter, "-1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
	}

	private ResultActions create(Member member, String json) throws Exception {
		return mockMvc.perform(post("/api/inquiries").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions list(Member member, String page) throws Exception {
		var request = get("/api/inquiries").header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()));
		if (page != null) {
			request.param("page", page);
		}
		return mockMvc.perform(request);
	}

	private static String body(String type, Long rentalId, String content) {
		return "{\"type\": \"" + type + "\", \"rentalId\": " + rentalId + ", \"content\": \"" + content + "\"}";
	}
}
