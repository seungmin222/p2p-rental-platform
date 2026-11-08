package com.team7.rental.payment.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.payment.dto.SettlementListRequest;
import com.team7.rental.payment.dto.SettlementListResponse;
import com.team7.rental.payment.service.SettlementQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 경로는 회원 아래지만 정산 원장이라 payment 도메인에 둔다 */
@Tag(name = "Payment", description = "결제·보증금")
@RestController
@RequestMapping("/api/members/me/settlements")
@RequiredArgsConstructor
public class MySettlementController {

	private final SettlementQueryService settlementQueryService;

	@Operation(summary = "정산 내역 조회 (REQ-039)", description = "본인이 제공자인 정산만, 최신 생성 순. 정산은 거래 완료 또는 수수료 있는 취소 때 "
		+ "생기며 받은 돈이 없거나 지급액이 0원이면 생기지 않는다. 지급 예정일에 지급 배치가 PAID로 바꾼다(실제 송금 없음). "
		+ "합계(scheduledTotal·paidTotal)는 페이지와 상관없이 전체 기준이다. 페이지 크기는 policies.md settlement.page로 고정이다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<SettlementListResponse> getMySettlements(@LoginMember
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		SettlementListRequest request) {
		return ApiResponse.ok(settlementQueryService.getMySettlements(memberId, request.page()));
	}
}
