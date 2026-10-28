package com.team7.rental.rental.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.service.RentalQueryService;
import com.team7.rental.rental.service.RentalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Rental", description = "예약·대여·반납")
@RestController
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
public class RentalController {

	private final RentalService rentalService;
	private final RentalQueryService rentalQueryService;

	@Operation(summary = "대여 예약 요청 (REQ-013, REQ-014)", description = "1순위 단계(결제 비활성)와 무료 대여는 바로 RESERVED, 결제를 켜면 PENDING_PAYMENT로 생성해 결제 대기 시간(policies.md payment.expire-minutes) 동안 기간을 선점한다. "
		+ "응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, RENTAL_INVALID_PERIOD, RENTAL_OWN_ITEM, RENTAL_OUT_OF_AVAILABLE_PERIOD,
		AUTH_UNAUTHORIZED, REGION_NOT_VERIFIED, ITEM_NOT_FOUND, RENTAL_PERIOD_CONFLICT, RENTAL_LOCK_TIMEOUT})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RentalDetailResponse> createRental(@LoginMember
	Long memberId,
		@Valid @RequestBody
		RentalCreateRequest request) {
		return ApiResponse.ok(rentalService.create(memberId, request));
	}

	@Operation(summary = "대여 상세 조회 (REQ-016)", description = "대여자·제공자만 조회할 수 있다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT, RENTAL_NOT_FOUND})
	@GetMapping("/{rentalId}")
	public ApiResponse<RentalDetailResponse> getRental(@LoginMember
	Long memberId, @PathVariable
	Long rentalId) {
		return ApiResponse.ok(rentalQueryService.getRental(rentalId, memberId));
	}
}
