package com.team7.rental.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 양측 동의 취소 응답 (REQ-015) */
public record CancelAgreementRespondRequest(
	@Schema(description = "ACCEPT면 대여가 취소(MUTUAL)되고, REJECT면 요청만 종료된다") @NotNull
	CancelAgreementAction action) {
}
