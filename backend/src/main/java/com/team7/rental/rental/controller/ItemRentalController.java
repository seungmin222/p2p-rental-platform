package com.team7.rental.rental.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.rental.dto.ItemRentalResponse;
import com.team7.rental.rental.service.RentalHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/** 경로는 물품 아래지만 대여 목록이라 rental 도메인에 둔다 (REQ-020 예약 현황) */
@Tag(name = "Rental", description = "예약·대여·반납")
@RestController
@RequiredArgsConstructor
public class ItemRentalController {

	private final RentalHistoryService rentalHistoryService;

	@Operation(summary = "물품별 예약 현황 (REQ-020)", description = "미구현: 응답 형태만 확정. 제공자 전용. 해당 물품의 대여 건 전부(CANCELED 포함). "
		+ "정렬: 다가오는·진행 중 건(PENDING_PAYMENT·RESERVED·RENTING·OVERDUE·RETURNED)을 시작일 오름차순으로 먼저, "
		+ "완료·취소 건(COMPLETED·CANCELED)을 시작일 내림차순으로 뒤에(같은 시작일은 id 순). "
		+ "페이지 크기는 policies.md item-rentals.page로 고정(size를 받지 않음). 없거나 삭제된 물품은 404 ITEM_NOT_FOUND, 남의 물품은 403 ITEM_NOT_OWNER.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND, ITEM_NOT_OWNER})
	@GetMapping("/api/items/{itemId}/rentals")
	public ApiResponse<PageResponse<ItemRentalResponse>> getItemRentals(@LoginMember
	Long memberId, @PathVariable
	Long itemId,
		@Parameter(description = "페이지 번호 (0부터). 생략하면 0", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero
		int page) {
		return ApiResponse.ok(rentalHistoryService.getItemRentals(itemId, memberId, page));
	}
}
