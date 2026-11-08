package com.team7.rental.ai.worker;

import static com.team7.rental.support.InspectionParts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.team7.rental.item.entity.Item;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.AiCompareStatus;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/**
 * 반납 요청 → ai.compare 큐(실제 RabbitMQ 컨테이너) → 리스너 → 결과 저장 (REQ-040). test 프로필은 리스너를 띄우지 않으므로 이 테스트만 큐를 비우고 켠다.
 */
class AiCompareQueueIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final String ALL_TRUE = """
		{"checklist": {"NO_EXTERIOR_DAMAGE": true, "ALL_PARTS_INCLUDED": true, "NO_CONTAMINATION": true,
		"WORKS_NORMALLY": true, "CONSUMABLES_RESTORED": true}}""";

	@Autowired
	private RabbitListenerEndpointRegistry listenerRegistry;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	@Autowired
	private ItemInspectionRepository inspectionRepository;

	@AfterEach
	void stopListener() {
		listenerRegistry.getListenerContainer(AiCompareListener.ID).stop();
	}

	@Test
	@DisplayName("반납 요청이 커밋되면 큐를 거쳐 비교되고 결과가 DONE으로 저장된다")
	void compareThroughQueue() throws Exception {
		// given: 앞 테스트들이 쌓은 메시지를 버리고 리스너를 켠다
		rabbitAdmin.purgeQueue(AiCompareQueue.NAME, false);
		listenerRegistry.getListenerContainer(AiCompareListener.ID).start();
		testClock.fixAt(START.atTime(10, 0));
		VerifiedMember verifiedOwner = fixture.createVerifiedMember();
		Member owner = verifiedOwner.member();
		Member renter = fixture.createVerifiedMember().member();
		Item item = fixture.createItem(verifiedOwner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).item();
		Rental rental = rentalFixture.createRental(item, renter, RentalStatus.RESERVED, START, END);
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/rentals/{rentalId}/handover", rental.getId())
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(owner.getId())))
			.andExpect(status().isOk());
		testClock.fixAt(END.atTime(18, 0));

		// when
		mockMvc.perform(multipart("/api/rentals/{rentalId}/return-request", rental.getId())
			.file(json(ALL_TRUE))
			.file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(renter.getId())))
			.andExpect(status().isOk());

		// then
		AiCompareStatus status = awaitStatus(rental, 15_000);
		assertThat(status).isEqualTo(AiCompareStatus.DONE);
		assertThat(aiClient.calls()).hasSize(1);
	}

	private AiCompareStatus awaitStatus(Rental rental, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			var record = inspectionRepository.findByRentalIdAndPhase(rental.getId(), InspectionPhase.RETURN)
				.orElseThrow()
				.getAiResult();
			if (record != null && !record.isProcessing()) {
				return record.status();
			}
			Thread.sleep(100);
		}
		return null;
	}
}
