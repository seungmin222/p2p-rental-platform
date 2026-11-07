package com.team7.rental.payment.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

class DeductionProposalTest {

	private static final int HOURS = 72;
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 7, 10, 0);
	private static final LocalDateTime DEADLINE = CREATED_AT.plusHours(HOURS);

	@Test
	@DisplayName("응답 기한 전에는 수락·거절할 수 있고, 정확히 기한이면 닫혀 자동 수락 대상이다")
	void deadline() {
		DeductionProposal proposal = proposed();

		assertThat(proposal.isOpen(DEADLINE.minusSeconds(1), HOURS)).isTrue();
		assertThat(proposal.isAutoAcceptDue(DEADLINE.minusSeconds(1), HOURS)).isFalse();
		assertThat(proposal.isOpen(DEADLINE, HOURS)).isFalse();
		assertThat(proposal.isAutoAcceptDue(DEADLINE, HOURS)).isTrue();
		assertClosed(() -> proposal.accept(DEADLINE, HOURS));
		assertClosed(() -> proposal.reject(DEADLINE, HOURS));
	}

	@Test
	@DisplayName("수락하면 ACCEPTED·응답 시각이 남고 다시 응답하거나 자동 수락할 수 없다")
	void acceptOnce() {
		DeductionProposal proposal = proposed();

		proposal.accept(CREATED_AT.plusHours(1), HOURS);

		assertThat(proposal.getStatus()).isEqualTo(DeductionProposalStatus.ACCEPTED);
		assertThat(proposal.getRespondedAt()).isEqualTo(CREATED_AT.plusHours(1));
		assertClosed(() -> proposal.reject(CREATED_AT.plusHours(2), HOURS));
		assertClosed(() -> proposal.autoAccept(DEADLINE, HOURS));
	}

	@Test
	@DisplayName("자동 수락은 기한이 지난 PROPOSED만 AUTO_ACCEPTED로 바꾼다")
	void autoAccept() {
		DeductionProposal proposal = proposed();
		assertClosed(() -> proposal.autoAccept(DEADLINE.minusSeconds(1), HOURS));

		proposal.autoAccept(DEADLINE, HOURS);

		assertThat(proposal.getStatus()).isEqualTo(DeductionProposalStatus.AUTO_ACCEPTED);
		assertThat(proposal.getRespondedAt()).isEqualTo(DEADLINE);
	}

	@Test
	@DisplayName("관리자 확정은 증빙 없이 ADMIN_DECIDED와 응답 시각으로 만들고, 영수증이 없으면 null로 둔다")
	void adminDecide() {
		DeductionProposal decision = DeductionProposal.adminDecide(1L, 9L, DamageGrade.C, 10_000, "확정", CREATED_AT);

		assertThat(decision.getStatus()).isEqualTo(DeductionProposalStatus.ADMIN_DECIDED);
		assertThat(decision.getRespondedAt()).isEqualTo(CREATED_AT);
		assertThat(decision.getEvidenceUrls()).isEmpty();
		assertThat(decision.getReceiptUrls()).isNull();
	}

	private static DeductionProposal proposed() {
		DeductionProposal proposal = DeductionProposal.propose(1L, 2L, DamageGrade.C, 20_000, "수리비",
			List.of("/files/deductions/a.jpg"), List.of("/files/deductions/b.jpg"));
		ReflectionTestUtils.setField(proposal, "createdAt", CREATED_AT);
		return proposal;
	}

	private static void assertClosed(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.DEDUCTION_PROPOSAL_CLOSED);
	}
}
