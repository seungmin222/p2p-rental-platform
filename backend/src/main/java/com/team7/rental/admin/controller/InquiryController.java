package com.team7.rental.admin.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.admin.dto.InquiryCreateRequest;
import com.team7.rental.admin.dto.InquiryListRequest;
import com.team7.rental.admin.dto.InquiryResponse;
import com.team7.rental.admin.service.InquiryQueryService;
import com.team7.rental.admin.service.InquiryService;
import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin", description = "관리자·문의")
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

	private final InquiryService inquiryService;
	private final InquiryQueryService inquiryQueryService;

	@Operation(summary = "문의·분쟁 접수 (REQ-063)", description = "일반 문의(GENERAL)는 대여 연결이 선택, 분쟁(DISPUTE)은 필수다. "
		+ "대여를 연결하면 본인이 대여자·제공자여야 한다. 분쟁은 결제 대기(PENDING_PAYMENT) 대여에는 접수할 수 없고, "
		+ "같은 대여에 처리 완료(RESOLVED)되지 않은 본인 분쟁이 있으면 409다. 분쟁을 접수하면 그 대여의 정산이 보류(HELD)되고 "
		+ "(정산이 아직 없으면 정산이 생길 때 보류), 이미 지급된 정산은 그대로다. 처리 상태는 RECEIVED로 시작하고 관리자가 바꾼다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT,
		INQUIRY_DISPUTE_NOT_ALLOWED, INQUIRY_DISPUTE_ALREADY_OPEN})
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	public ApiResponse<InquiryResponse> create(@LoginMember
	Long memberId, @Valid @RequestBody
	InquiryCreateRequest request) {
		return ApiResponse.ok(inquiryService.create(memberId, request));
	}

	@Operation(summary = "내 문의 목록 (REQ-063)", description = "본인이 접수한 문의·분쟁, 최신 접수 순. 처리 상태(status)를 함께 준다. "
		+ "페이지 크기는 policies.md inquiry.page로 고정이다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<PageResponse<InquiryResponse>> getMyInquiries(@LoginMember
	Long memberId, @ParameterObject @Valid @ModelAttribute
	InquiryListRequest request) {
		return ApiResponse.ok(inquiryQueryService.getMyInquiries(memberId, request.page()));
	}
}
