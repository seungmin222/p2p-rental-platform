package com.team7.rental.admin.controller;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.payment.entity.Settlement;
import com.team7.rental.payment.entity.SettlementStatus;
import com.team7.rental.payment.repository.SettlementRepository;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 관리자 문의 처리·분쟁 종료 (REQ-057). 접수·완료도 실제 API로 해서 트랜잭션·락 경계를 운영과 같게 둔다. 대여료 30,000원(3일 × 10,000원), 수수료 10% →
 * 지급액 27,000원.
 */
class AdminInquiryIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime RETURNED_AT = END.atTime(18, 0);
	private static final LocalDateTime NOW = RETURNED_AT.plusDays(1);
	private static final String NORMAL = "{\"result\": \"NORMAL\"}";
	private static final long ADMIN_ID = 9_999L;

	@Autowired
	private SettlementRepository settlementRepository;

	private Member owner;
	private Member renter;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		owner = verifiedOwner.member();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)).item();
		rental = rentalFixture.createReturned(item, renter, START, END, RETURNED_AT);
		paymentFixture.createApproved(rental);
	}

	@Test
	@DisplayName("관리자 목록은 ADMIN만, 접수 순이고 처리 상태·종류로 거른다")
	void list() throws Exception {
		long general = create(renter, "GENERAL", null);
		long dispute = create(owner, "DISPUTE", rental.getId());
		long resolved = create(renter, "GENERAL", null);
		process(resolved, "RESOLVED", "안내드렸습니다.").andExpect(status().isOk());

		mockMvc.perform(get("/api/admin/inquiries").header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
		list("").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(3))
			.andExpect(jsonPath("$.data.items[0].inquiryId").value(general))
			.andExpect(jsonPath("$.data.items[0].memberId").value(renter.getId()))
			.andExpect(jsonPath("$.data.items[1].inquiryId").value(dispute))
			.andExpect(jsonPath("$.data.size").value(20));
		list("?status=RECEIVED").andExpect(jsonPath("$.data.items.length()").value(2));
		list("?status=RECEIVED&type=DISPUTE").andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].inquiryId").value(dispute));
		list("?status=RESOLVED").andExpect(jsonPath("$.data.items[0].answer").value("안내드렸습니다."));
		list("?status=DONE").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("일반 문의: RECEIVED → IN_PROGRESS → RESOLVED, 답변은 내 문의 목록에도 보인다")
	void processGeneral() throws Exception {
		long id = create(renter, "GENERAL", null);

		process(id, "IN_PROGRESS", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.data.answer").doesNotExist());
		testClock.fixAt(NOW.plusHours(2));
		process(id, "RESOLVED", "결제 수단은 결제 화면에서 바꿀 수 있습니다.").andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESOLVED"))
			.andExpect(jsonPath("$.data.answeredAt").value("2026-11-06T20:00:00"));

		mockMvc.perform(get("/api/inquiries").header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(jsonPath("$.data.items[0].status").value("RESOLVED"))
			.andExpect(jsonPath("$.data.items[0].answer").value("결제 수단은 결제 화면에서 바꿀 수 있습니다."))
			.andExpect(jsonPath("$.data.items[0].answeredAt").exists());
	}

	@Test
	@DisplayName("RECEIVED에서 바로 RESOLVED할 수 있고, RESOLVED는 되돌릴 수 없다(409). 같은 상태로도 409")
	void transitions() throws Exception {
		long id = create(renter, "GENERAL", null);
		process(id, "RESOLVED", "답변").andExpect(status().isOk());

		process(id, "IN_PROGRESS", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("INQUIRY_INVALID_TRANSITION"));
		process(id, "RESOLVED", "다시").andExpect(status().isConflict());

		long other = create(renter, "GENERAL", null);
		process(other, "IN_PROGRESS", null).andExpect(status().isOk());
		process(other, "IN_PROGRESS", null).andExpect(status().isConflict());
		process(other, "RECEIVED", null).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("답변 검사: RESOLVED에 답변 없음 400, 공백만 400, 1,001자 400(1,000자는 통과), 이미 남긴 답변이 있으면 생략 가능")
	void answerValidation() throws Exception {
		long id = create(renter, "GENERAL", null);

		process(id, "RESOLVED", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("answer"));
		process(id, "IN_PROGRESS", "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("answer"));
		process(id, "IN_PROGRESS", "가".repeat(1_001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[0].field").value("answer"));
		process(id, "IN_PROGRESS", "가".repeat(1_000)).andExpect(status().isOk());

		process(id, "RESOLVED", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.answer").value("가".repeat(1_000)));
	}

	@Test
	@DisplayName("없는 문의 404, 일반 회원 403, status 누락 400")
	void errors() throws Exception {
		process(12_345L, "IN_PROGRESS", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("INQUIRY_NOT_FOUND"));
		long id = create(renter, "GENERAL", null);
		mockMvc.perform(patch("/api/admin/inquiries/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"RESOLVED\", \"answer\": \"셀프 처리\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/admin/inquiries/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, adminBearerToken(ADMIN_ID))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answer\": \"상태 없음\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("분쟁 처리 완료: 정산 HELD → SCHEDULED, 지급 예정일은 그대로다. 처리 중으로 바꾸는 것만으로는 풀리지 않는다")
	void resolveDisputeReleases() throws Exception {
		confirmNormal().andExpect(status().isOk());
		LocalDate scheduledDate = settlement().getScheduledDate();
		long id = create(renter, "DISPUTE", rental.getId());
		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.HELD);

		process(id, "IN_PROGRESS", "확인 중입니다.").andExpect(status().isOk());
		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.HELD);

		process(id, "RESOLVED", "정상 반납으로 판단했습니다.").andExpect(status().isOk());
		Settlement after = settlement();
		assertThat(after.getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
		assertThat(after.getScheduledDate()).isEqualTo(scheduledDate);
	}

	@Test
	@DisplayName("양측 분쟁: 하나만 처리 완료하면 HELD 그대로, 둘 다 끝나면 SCHEDULED")
	void bothDisputes() throws Exception {
		confirmNormal().andExpect(status().isOk());
		long byRenter = create(renter, "DISPUTE", rental.getId());
		long byOwner = create(owner, "DISPUTE", rental.getId());

		process(byRenter, "RESOLVED", "처리했습니다.").andExpect(status().isOk());
		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.HELD);

		process(byOwner, "RESOLVED", "처리했습니다.").andExpect(status().isOk());
		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
	}

	@Test
	@DisplayName("정산이 생기기 전에 분쟁을 끝내면, 대여 완료 때 정산이 SCHEDULED로 생긴다")
	void resolveBeforeCompletion() throws Exception {
		long id = create(renter, "DISPUTE", rental.getId());
		process(id, "RESOLVED", "처리했습니다.").andExpect(status().isOk());
		assertThat(settlementRepository.findAll()).isEmpty();

		confirmNormal().andExpect(status().isOk());

		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.SCHEDULED);
	}

	@Test
	@DisplayName("처리 완료 뒤 같은 회원이 다시 분쟁을 접수하면 다시 HELD가 된다")
	void reopenAfterResolve() throws Exception {
		confirmNormal().andExpect(status().isOk());
		long first = create(renter, "DISPUTE", rental.getId());
		process(first, "RESOLVED", "처리했습니다.").andExpect(status().isOk());
		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.SCHEDULED);

		create(renter, "DISPUTE", rental.getId());

		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.HELD);
	}

	@Test
	@DisplayName("이미 지급(PAID)된 정산은 분쟁을 끝내도 PAID 그대로다")
	void paidStaysPaid() throws Exception {
		confirmNormal().andExpect(status().isOk());
		Settlement settlement = settlement();
		LocalDateTime paidAt = settlement.getScheduledDate().atTime(0, 30);
		settlement.pay(paidAt);
		settlementRepository.save(settlement);
		testClock.fixAt(paidAt.plusHours(1));
		long id = create(renter, "DISPUTE", rental.getId());

		process(id, "RESOLVED", "이미 지급된 건입니다.").andExpect(status().isOk());

		assertThat(settlement().getStatus()).isEqualTo(SettlementStatus.PAID);
	}

	private long create(Member member, String type, Long rentalId) throws Exception {
		String body = mockMvc.perform(post("/api/inquiries")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(member.getId()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\": \"" + type + "\", \"rentalId\": " + rentalId + ", \"content\": \"문의합니다.\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number)JsonPath.read(body, "$.data.inquiryId")).longValue();
	}

	private ResultActions process(long inquiryId, String status, String answer) throws Exception {
		// 테스트 답변에는 따옴표·역슬래시가 없다
		String answerJson = answer == null ? "null" : "\"" + answer + "\"";
		return mockMvc.perform(patch("/api/admin/inquiries/{id}", inquiryId)
			.header(HttpHeaders.AUTHORIZATION, adminBearerToken(ADMIN_ID))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"" + status + "\", \"answer\": " + answerJson + "}"));
	}

	private ResultActions list(String query) throws Exception {
		return mockMvc.perform(get("/api/admin/inquiries" + query)
			.header(HttpHeaders.AUTHORIZATION, adminBearerToken(ADMIN_ID)));
	}

	private ResultActions confirmNormal() throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/return", rental.getId())
			.file(json(NORMAL))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())));
	}

	private Settlement settlement() {
		return settlementRepository.findByRentalId(rental.getId()).orElseThrow();
	}
}
