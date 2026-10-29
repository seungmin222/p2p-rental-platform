package com.team7.rental.item.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.MyItemRequest;
import com.team7.rental.item.dto.MyItemResponse;
import com.team7.rental.item.service.ItemQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 내 물품. 경로는 회원 아래지만 물품 목록이라 item 도메인에 둔다. 태그는 api.md 섹션을 따른다: 목록은 대여(REQ-020), 수정 화면은 물품(REQ-007).
 */
@RestController
@RequestMapping("/api/members/me/items")
@RequiredArgsConstructor
public class MyItemController {

	private final ItemQueryService itemQueryService;

	@Tag(name = "Rental", description = "예약·대여·반납")
	@Operation(summary = "내 물품 관리 (REQ-020)", description = "미구현: 응답 형태만 확정. 탭: RENTING = RENTING·OVERDUE 대여가 있는 물품, AVAILABLE = 그 외 공개 물품, "
		+ "HIDDEN = 숨김 물품(숨기기 REQ-044 전에는 항상 빈 목록). 삭제된 물품은 없다. 정렬은 최근 등록(itemId 내림차순). "
		+ "페이지 크기는 policies.md my-items.page로 고정(size를 받지 않음).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<PageResponse<MyItemResponse>> getMyItems(@LoginMember
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		MyItemRequest request) {
		return ApiResponse.ok(itemQueryService.getMyItems(memberId, request));
	}

	@Tag(name = "Item", description = "물품·피드·검색·카테고리")
	@Operation(summary = "물품 수정 화면 조회 (REQ-007)", description = "미구현: 응답 형태만 확정. 등록자 전용. 물품 가격(itemPrice)·대여 가능 기간·이미지 ID를 포함해 수정 폼을 채운다. "
		+ "없거나 삭제된 물품은 404 ITEM_NOT_FOUND, 남의 물품은 403 ITEM_NOT_OWNER.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND, ITEM_NOT_OWNER})
	@GetMapping("/{itemId}")
	public ApiResponse<ItemEditResponse> getMyItemForEdit(@LoginMember
	Long memberId, @PathVariable
	Long itemId) {
		return ApiResponse.ok(itemQueryService.getEditView(itemId, memberId));
	}
}
