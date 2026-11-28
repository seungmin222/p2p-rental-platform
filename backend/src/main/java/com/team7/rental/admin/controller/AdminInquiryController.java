package com.team7.rental.admin.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.admin.dto.AdminInquiryListRequest;
import com.team7.rental.admin.dto.AdminInquiryProcessRequest;
import com.team7.rental.admin.dto.AdminInquiryResponse;
import com.team7.rental.admin.service.InquiryAdminService;
import com.team7.rental.admin.service.InquiryQueryService;
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

/** 관리자 문의 처리 (REQ-057). /api/admin/** 는 ADMIN만(SecurityConfig) */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

	private final InquiryAdminService inquiryAdminService;
	private final InquiryQueryService inquiryQueryService;

	@Operation(summary = "관리자 문의 목록 (REQ-057)", description = "일반 문의·분쟁 전체, 접수 순(오래된 것 먼저). 처리 상태(status)·종류(type)로 거를 수 있다. "
		+ "페이지 크기는 policies.md inquiry.page로 고정이다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, AUTH_FORBIDDEN})
	@GetMapping
	public ApiResponse<PageResponse<AdminInquiryResponse>> getInquiries(@ParameterObject @Valid @ModelAttribute
	AdminInquiryListRequest request) {
		return ApiResponse.ok(inquiryQueryService.getForAdmin(request));
	}

	@Operation(summary = "관리자 문의 처리·분쟁 종료 (REQ-057)", description = "처리 상태를 바꾸고 답변을 남긴다. "
		+ "전이: RECEIVED → IN_PROGRESS, RECEIVED·IN_PROGRESS → RESOLVED. RESOLVED는 되돌릴 수 없고 같은 상태로는 바꾸지 않는다(409). "
		+ "RESOLVED에는 답변이 필요하다(이미 남긴 답변이 있으면 생략 가능). 답변은 공백만 불가, policies.md inquiry.answer-max 이하. "
		+ "분쟁(DISPUTE)을 RESOLVED로 바꾸면, 그 대여에 처리 완료되지 않은 분쟁(상대방 것 포함)이 더 없을 때 정산 보류를 푼다(HELD → SCHEDULED, "
		+ "지급 예정일은 그대로라 지났으면 다음 지급 배치가 지급). 알림은 보내지 않는다. "
		+ "검사 순서: 400 COMMON_INVALID_INPUT(답변 형식) → 404 INQUIRY_NOT_FOUND → 409 INQUIRY_INVALID_TRANSITION → 400 COMMON_INVALID_INPUT(답변 없음).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, AUTH_FORBIDDEN, INQUIRY_NOT_FOUND,
		INQUIRY_INVALID_TRANSITION})
	@PatchMapping("/{inquiryId}")
	public ApiResponse<AdminInquiryResponse> process(@LoginMember
	Long adminId, @PathVariable
	Long inquiryId, @Valid @RequestBody
	AdminInquiryProcessRequest request) {
		return ApiResponse.ok(inquiryAdminService.process(adminId, inquiryId, request));
	}
}
