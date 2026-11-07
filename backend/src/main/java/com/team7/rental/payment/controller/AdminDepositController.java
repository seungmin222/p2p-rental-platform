package com.team7.rental.payment.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.payment.dto.AdminDepositDecisionRequest;
import com.team7.rental.payment.dto.HeldDepositResponse;
import com.team7.rental.payment.service.DepositDeductionService;
import com.team7.rental.rental.dto.RentalDepositInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관리자 보증금 확정 API 2개 (REQ-049, 관리자 화면 REQ-057에서 2순위로 앞당김). /api/admin/** 는 ADMIN만(SecurityConfig) */
@Tag(name = "Admin", description = "관리자")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDepositController {

	private final DepositDeductionService depositDeductionService;

	@Operation(summary = "보류 기한 초과 보증금 목록 (REQ-049, REQ-057)", description = "보증금 보류(HELD) 후 policies.md deposit.hold-agreement-days가 지나도록 합의하지 못한 건, "
		+ "보류가 오래된 순. 반환이 진행 중인 건은 빠진다. 페이지 없이 전체를 돌려준다(관리자 목록 페이지 크기 정책이 없다).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED, AUTH_FORBIDDEN})
	@GetMapping("/deposits/held")
	public ApiResponse<List<HeldDepositResponse>> findHeld() {
		return ApiResponse.ok(depositDeductionService.findHeld());
	}

	@Operation(summary = "보증금 차감 관리자 확정 (REQ-049, REQ-057)", description = "보류 후 policies.md deposit.hold-agreement-days가 지난 건의 차감액을 관리자가 확정한다. "
		+ "상한 검사는 제공자 제안과 같다(A 불가, 미반납 신고 건은 D만, 0 ≤ amount ≤ min(상한, 남은 보증금)). 제안 이력에 ADMIN_DECIDED로 남는다. "
		+ "남은 보증금 − 차감액을 부분 취소로 돌려주고(0원이면 FORFEITED) 거래 완료(COMPLETED), 양측에 DEDUCTION_ACCEPTED. "
		+ "대여자 응답을 기다리는 제안이 있으면 409 DEDUCTION_PROPOSAL_IN_PROGRESS(그 결과를 기다린다). "
		+ "검사 순서: 400 COMMON_INVALID_INPUT → 404 RENTAL_NOT_FOUND → 409 RENTAL_INVALID_TRANSITION → 409 DEPOSIT_NOT_HELD → "
		+ "409 DEPOSIT_ADMIN_DECISION_TOO_EARLY → 409 DEDUCTION_PROPOSAL_IN_PROGRESS → 400 DEDUCTION_GRADE_NOT_ALLOWED → "
		+ "400 DEDUCTION_EXCEEDS_DEPOSIT → 400 DEDUCTION_EXCEEDS_CAP. 응답은 확정 뒤 보증금 정보다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, DEDUCTION_GRADE_NOT_ALLOWED, DEDUCTION_EXCEEDS_DEPOSIT, DEDUCTION_EXCEEDS_CAP,
		AUTH_UNAUTHORIZED, AUTH_FORBIDDEN, RENTAL_NOT_FOUND, RENTAL_INVALID_TRANSITION, DEPOSIT_NOT_HELD,
		DEPOSIT_ADMIN_DECISION_TOO_EARLY, DEDUCTION_PROPOSAL_IN_PROGRESS})
	@PatchMapping("/rentals/{rentalId}/deposit")
	public ApiResponse<RentalDepositInfo> decide(@LoginMember
	Long adminId, @PathVariable
	Long rentalId,
		@Valid @RequestBody
		AdminDepositDecisionRequest request) {
		return ApiResponse.ok(depositDeductionService.adminDecide(rentalId, adminId, request));
	}
}
