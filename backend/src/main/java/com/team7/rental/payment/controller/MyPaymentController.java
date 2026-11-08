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
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.payment.dto.PaymentHistoryRequest;
import com.team7.rental.payment.dto.PaymentHistoryResponse;
import com.team7.rental.payment.service.PaymentHistoryQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 경로는 회원 아래지만 결제 목록이라 payment 도메인에 둔다. 제공자 쪽은 정산 내역(REQ-039) */
@Tag(name = "Payment", description = "결제·보증금")
@RestController
@RequestMapping("/api/members/me/payments")
@RequiredArgsConstructor
public class MyPaymentController {

	private final PaymentHistoryQueryService paymentHistoryQueryService;

	@Operation(summary = "결제·보증금 내역 조회 (REQ-033)", description = "본인이 대여자인 결제만, 한 항목 = 결제 한 건(주문만 만들고 승인되지 않은 "
		+ "READY·FAILED 제외). 최신 결제 순. type(임시 해석, open-issues #44): ALL·PAYMENT = 승인된 결제 전체, REFUND = 취소 환불이 있는 건, "
		+ "DEPOSIT = 보증금이 HELD·REFUNDED·FORFEITED·REFUND_FAILED인 건. from·to는 결제 시각(createdAt)의 날짜에 걸리고 둘 다 포함한다. "
		+ "생략하면 to = 오늘, from = to − policies.md payment-history.default-range(3개월). from > to면 400. "
		+ "페이지 크기는 policies.md payment-history.page로 고정이다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<PageResponse<PaymentHistoryResponse>> getMyPayments(@LoginMember
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		PaymentHistoryRequest request) {
		return ApiResponse.ok(paymentHistoryQueryService.getMyPayments(memberId, request));
	}
}
