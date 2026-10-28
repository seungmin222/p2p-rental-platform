package com.team7.rental.rental.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.rental.dto.RentalCalendarResponse;
import com.team7.rental.rental.service.RentalQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 경로는 물품 아래지만 대여 기간 점유를 보여 주므로 rental 도메인에 둔다(docs/domains/rental.md REQ-012) */
@Tag(name = "Rental", description = "예약·대여·반납")
@RestController
@RequiredArgsConstructor
public class RentalCalendarController {

	private final RentalQueryService rentalQueryService;

	@Operation(summary = "대여 캘린더 조회 (REQ-012)", description = "비회원도 조회할 수 있다. 숨김 물품은 소유자만 조회할 수 있고 그 외에는 404다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, ITEM_NOT_FOUND})
	@GetMapping("/api/items/{itemId}/calendar")
	public ApiResponse<RentalCalendarResponse> getCalendar(@LoginMember(required = false)
	Long memberId,
		@PathVariable
		Long itemId,
		@Parameter(description = "조회할 달 (YYYY-MM). 생략하면 이번 달", example = "2026-10") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM")
		YearMonth month) {
		return ApiResponse.ok(rentalQueryService.getCalendar(itemId, month, memberId));
	}
}
