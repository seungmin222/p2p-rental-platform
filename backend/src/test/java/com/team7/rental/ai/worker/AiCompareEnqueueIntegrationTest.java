package com.team7.rental.ai.worker;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.service.RentalInspectionTxService;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;
import com.team7.rental.support.RentalFixture;

/**
 * 반납 요청 커밋 뒤 ai.compare 적재 (REQ-040). 큐를 목으로 바꿔 적재 시점과 실패를 본다(이 클래스만 컨텍스트가 따로 뜬다).
 */
class AiCompareEnqueueIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";

	@MockitoBean
	private AiCompareQueue aiCompareQueue;

	@Autowired
	private RentalRepository rentalRepository;

	@Autowired
	private RentalInspectionTxService rentalInspectionTxService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Member renter;
	private Rental rental;

	@BeforeEach
	void setUp() {
		testClock.fixAt(END.atTime(18, 0));
		VerifiedMember owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		rental = rentalFixture.createRental(item, renter, RentalStatus.RENTING, START, END);
	}

	@Test
	@DisplayName("반납 요청이 커밋되면 대여 ID로 한 번 적재한다")
	void enqueueAfterCommit() throws Exception {
		// when
		requestReturn().andExpect(status().isOk());

		// then
		then(aiCompareQueue).should().publish(rental.getId());
	}

	@Test
	@DisplayName("적재가 실패해도 반납 요청은 200 RETURNED로 끝난다")
	void enqueueFailureDoesNotBreakReturn() throws Exception {
		// given
		willThrow(new AmqpConnectException(new java.net.ConnectException("broker down"))).given(aiCompareQueue)
			.publish(anyLong());

		// when, then
		requestReturn()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RETURNED"));
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RETURNED);
	}

	@Test
	@DisplayName("반납 요청 트랜잭션이 롤백되면 적재하지 않는다")
	void noEnqueueOnRollback() {
		// when
		transactionTemplate.executeWithoutResult(status -> {
			rentalInspectionTxService.requestReturn(rental.getId(), renter.getId(), RentalFixture.allChecked(), null,
				List.of("/files/inspections/a.jpg"));
			status.setRollbackOnly();
		});

		// then
		then(aiCompareQueue).shouldHaveNoInteractions();
		assertThat(rentalRepository.findById(rental.getId()).orElseThrow().getStatus())
			.isEqualTo(RentalStatus.RENTING);
	}

	@Test
	@DisplayName("거절된 반납 요청(상태 오류)은 적재하지 않는다")
	void noEnqueueOnRejectedRequest() throws Exception {
		// given
		requestReturn().andExpect(status().isOk());
		clearInvocations(aiCompareQueue);

		// when, then
		requestReturn().andExpect(status().isConflict());
		then(aiCompareQueue).shouldHaveNoInteractions();
	}

	private ResultActions requestReturn() throws Exception {
		return mockMvc.perform(multipart("/api/rentals/{rentalId}/return-request", rental.getId())
			.file(json(ALL_TRUE))
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())));
	}
}
