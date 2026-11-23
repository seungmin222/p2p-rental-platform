package com.team7.rental.item.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.item.dto.WishItemResponse;
import com.team7.rental.item.dto.WishListRequest;
import com.team7.rental.item.dto.WishResponse;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.item.service.WishService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 관심(찜). 내 관심 목록은 경로가 회원 아래지만 물품 목록이라 item 도메인에 둔다(내 물품 MyItemController와 같은 방식). */
@Tag(name = "Item", description = "물품·피드·검색·카테고리")
@RestController
@RequiredArgsConstructor
public class WishController {

	private final WishService wishService;
	private final ItemQueryService itemQueryService;

	@Operation(summary = "관심 추가 (REQ-022)", description = "멱등: 이미 관심이면 아무것도 바꾸지 않고 200으로 현재 상태를 돌려준다. "
		+ "새로 담을 때만 물품 관심 수(wishCount)가 1 오른다. 응답은 처리 뒤의 isWished(항상 true)·wishCount. "
		+ "상세 조회와 같은 공개 규칙: 없거나 삭제된 물품, 남의 숨김 물품은 404 ITEM_NOT_FOUND. 본인 물품은 400 WISH_OWN_ITEM.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND, WISH_OWN_ITEM})
	@PostMapping("/api/items/{itemId}/wishes")
	public ApiResponse<WishResponse> addWish(@LoginMember
	Long memberId, @PathVariable
	Long itemId) {
		return ApiResponse.ok(wishService.add(memberId, itemId));
	}

	@Operation(summary = "관심 해제 (REQ-022)", description = "멱등: 관심이 아니면 아무것도 바꾸지 않고 200으로 현재 상태를 돌려준다. "
		+ "실제로 해제했을 때만 wishCount가 1 내려가며 0 아래로는 내려가지 않는다. 응답은 처리 뒤의 isWished(항상 false)·wishCount. "
		+ "관심 목록에 남은 항목을 지울 수 있도록 삭제·숨김 물품도 해제할 수 있다. 없는 물품만 404 ITEM_NOT_FOUND.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND})
	@DeleteMapping("/api/items/{itemId}/wishes")
	public ApiResponse<WishResponse> removeWish(@LoginMember
	Long memberId, @PathVariable
	Long itemId) {
		return ApiResponse.ok(wishService.remove(memberId, itemId));
	}

	@Operation(summary = "내 관심 목록 (REQ-022)", description = "최근 추가순. 페이지 크기는 policies.md wish.page로 고정(size를 받지 않음). "
		+ "삭제(DELETED)·숨김(HIDDEN) 물품도 status와 함께 남는다: DELETED는 \"삭제된 물품\", HIDDEN은 \"볼 수 없는 물품\"으로 표시하고 "
		+ "두 경우 모두 상세는 404다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping("/api/members/me/wishes")
	public ApiResponse<PageResponse<WishItemResponse>> getMyWishes(@LoginMember
	Long memberId, @ParameterObject @Valid @ModelAttribute
	WishListRequest request) {
		return ApiResponse.ok(itemQueryService.getWishes(memberId, request.page()));
	}
}
