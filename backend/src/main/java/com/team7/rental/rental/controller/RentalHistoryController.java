package com.team7.rental.rental.controller;

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
import com.team7.rental.rental.dto.RentalHistoryRequest;
import com.team7.rental.rental.dto.RentalHistoryResponse;
import com.team7.rental.rental.service.RentalHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 경로는 회원 아래지만 대여 목록이라 rental 도메인에 둔다. 제공자 쪽 목록은 REQ-020 */
@Tag(name = "Rental", description = "예약·대여·반납")
@RestController
@RequestMapping("/api/members/me/rentals")
@RequiredArgsConstructor
public class RentalHistoryController {

	private final RentalHistoryService rentalHistoryService;

	@Operation(summary = "빌린 내역 조회 (REQ-019)", description = "본인이 대여자인 건만. 탭별 상태: UPCOMING = PENDING_PAYMENT·RESERVED, "
		+ "ONGOING = RENTING·OVERDUE·RETURNED, DONE = COMPLETED·CANCELED. "
		+ "정렬은 모든 탭에서 OVERDUE 먼저, 그다음 startDate 내림차순, id 내림차순. 페이지 크기는 policies.md rental-history.page로 고정이다. "
		+ "canWriteReview는 COMPLETED이고 completedAt + review.write-deadline-days 이내(정확히 기한 시각 포함)면 true다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<PageResponse<RentalHistoryResponse>> getRentalHistory(@LoginMember
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		RentalHistoryRequest request) {
		return ApiResponse.ok(rentalHistoryService.getRenterHistory(memberId, request.tab(), request.page()));
	}
}
