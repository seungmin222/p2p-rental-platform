package com.team7.rental.admin.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 문의 처리 상태 전이 (REQ-057, state-machines §5) */
class InquiryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

	@ParameterizedTest(name = "{0} → {1}: {2}")
	@CsvSource({
		"RECEIVED, RECEIVED, false",
		"RECEIVED, IN_PROGRESS, true",
		"RECEIVED, RESOLVED, true",
		"IN_PROGRESS, RECEIVED, false",
		"IN_PROGRESS, IN_PROGRESS, false",
		"IN_PROGRESS, RESOLVED, true",
		"RESOLVED, RECEIVED, false",
		"RESOLVED, IN_PROGRESS, false",
		"RESOLVED, RESOLVED, false"})
	@DisplayName("허용 전이는 RECEIVED → IN_PROGRESS·RESOLVED, IN_PROGRESS → RESOLVED뿐이다")
	void canTransitTo(InquiryStatus from, InquiryStatus to, boolean allowed) {
		Inquiry inquiry = inquiryIn(from);

		assertThat(inquiry.canTransitTo(to)).isEqualTo(allowed);
		if (!allowed) {
			assertThatThrownBy(() -> inquiry.process(to, "답변", NOW)).isInstanceOf(IllegalStateException.class);
			assertThat(inquiry.getStatus()).isEqualTo(from);
		}
	}

	@Test
	@DisplayName("처리 중에 남긴 답변이 있으면 답변 없이 처리 완료할 수 있고, 답변 시각은 마지막으로 쓴 때다")
	void resolveWithEarlierAnswer() {
		Inquiry inquiry = Inquiry.receive(1L, 10L, InquiryType.DISPUTE, "이의 있습니다.");
		inquiry.process(InquiryStatus.IN_PROGRESS, "검토 중입니다.", NOW);

		inquiry.process(InquiryStatus.RESOLVED, null, NOW.plusDays(1));

		assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.RESOLVED);
		assertThat(inquiry.getAnswer()).isEqualTo("검토 중입니다.");
		assertThat(inquiry.getAnsweredAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("답변이 한 번도 없으면 처리 완료할 수 없다")
	void resolveWithoutAnswer() {
		Inquiry inquiry = Inquiry.receive(1L, null, InquiryType.GENERAL, "문의합니다.");

		assertThatThrownBy(() -> inquiry.process(InquiryStatus.RESOLVED, null, NOW))
			.isInstanceOf(IllegalStateException.class);
		assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.RECEIVED);
	}

	@Test
	@DisplayName("답변 없이 처리 중으로 바꿀 수 있다")
	void inProgressWithoutAnswer() {
		Inquiry inquiry = Inquiry.receive(1L, null, InquiryType.GENERAL, "문의합니다.");

		inquiry.process(InquiryStatus.IN_PROGRESS, null, NOW);

		assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
		assertThat(inquiry.getAnswer()).isNull();
		assertThat(inquiry.getAnsweredAt()).isNull();
	}

	private Inquiry inquiryIn(InquiryStatus status) {
		Inquiry inquiry = Inquiry.receive(1L, null, InquiryType.GENERAL, "문의합니다.");
		if (status == InquiryStatus.IN_PROGRESS) {
			inquiry.process(InquiryStatus.IN_PROGRESS, null, NOW);
		} else if (status == InquiryStatus.RESOLVED) {
			inquiry.process(InquiryStatus.RESOLVED, "처리했습니다.", NOW);
		}
		return inquiry;
	}
}
