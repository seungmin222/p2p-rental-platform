package com.team7.rental.payment.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.payment.dto.PaymentConfirmRequest;
import com.team7.rental.payment.dto.PaymentConfirmResponse;
import com.team7.rental.payment.dto.PaymentCreateRequest;
import com.team7.rental.payment.dto.PaymentOrderResponse;
import com.team7.rental.payment.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payment", description = "결제·보증금")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	@Operation(summary = "결제 주문 생성 (REQ-024, REQ-038)", description = "결제 대기(PENDING_PAYMENT)이고 만료 전인 대여의 주문을 만든다. 금액은 서버가 계산한다. "
		+ "진행 중인 주문이 있으면 같은 주문을 돌려주고, 승인에 실패한 뒤에는 새 주문을 만든다. 무료 대여는 결제 없이 바로 RESERVED라 이 API를 쓰지 않는다. "
		+ "extensionId를 보내면 그 연장(결제 대기이고 만료 전)의 연장료 주문을 만든다(REQ-038). 다른 대여의 연장이면 404 RENTAL_EXTENSION_NOT_FOUND, 결제 대기가 아니면 409 RENTAL_INVALID_TRANSITION.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, PAYMENT_RENTER_ONLY, RENTAL_NOT_FOUND,
		RENTAL_EXTENSION_NOT_FOUND, RENTAL_INVALID_TRANSITION, PAYMENT_EXPIRED})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PaymentOrderResponse> createOrder(@LoginMember
	Long memberId,
		@Valid @RequestBody
		PaymentCreateRequest request) {
		return ApiResponse.ok(paymentService.createOrder(request.rentalId(), request.extensionId(), memberId));
	}

	@Operation(summary = "결제 승인 (REQ-024)", description = "토스 결제창 성공 후 호출한다. 승인되면 대여가 RESERVED, 보증금이 FROZEN(0원이면 NONE)이 된다. "
		+ "이미 처리한 orderId는 기존 결과를 돌려준다. 금액이 다르면 400과 함께 대여가 취소된다. "
		+ "PAYMENT_APPROVAL_FAILED는 대여가 결제 대기로 남아 새 주문으로 다시 결제할 수 있다. "
		+ "PAYMENT_RESULT_UNKNOWN은 같은 요청을 다시 보내도 중복 승인되지 않는다. "
		+ "PAYMENT_CANCELED_AFTER_EXPIRY는 승인 직후 대여가 만료된 경우로, 전액 환불을 진행한다. "
		+ "연장 결제(type=EXTENSION, REQ-038)는 승인되면 연장이 CONFIRMED가 되고 대여 종료일이 바뀐다(보증금 상태 없음). 금액이 다르면 연장이 취소된다. "
		+ "승인 시점에 연장이 만료됐거나 대여가 대여 중(RENTING)이 아니면(연체 전환·반납과의 경합) 연장을 취소하고 연장료를 전액 환불하며 PAYMENT_CANCELED_AFTER_EXPIRY로 답한다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, PAYMENT_AMOUNT_MISMATCH, PAYMENT_APPROVAL_FAILED, AUTH_UNAUTHORIZED,
		PAYMENT_RENTER_ONLY, PAYMENT_NOT_FOUND, PAYMENT_INVALID_STATUS, PAYMENT_EXPIRED, PAYMENT_CANCELED_AFTER_EXPIRY,
		PAYMENT_RESULT_UNKNOWN})
	@PostMapping("/confirm")
	public ApiResponse<PaymentConfirmResponse> confirm(@LoginMember
	Long memberId,
		@Valid @RequestBody
		PaymentConfirmRequest request) {
		return ApiResponse.ok(paymentService.confirm(memberId, request));
	}
}
