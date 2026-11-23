package com.team7.rental.item.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.item.dto.ItemSummaryResponse;
import com.team7.rental.item.service.ItemQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 최근 본 상품. 경로는 회원 아래지만 물품 목록이라 item 도메인에 둔다(내 물품 MyItemController와 같은 방식). */
@Tag(name = "Item", description = "물품·피드·검색·카테고리")
@RestController
@RequiredArgsConstructor
public class RecentItemController {

	private final ItemQueryService itemQueryService;

	@Operation(summary = "최근 본 상품 (REQ-035)", description = "로그인 회원이 상세를 본 남의 물품을 최근 본 순으로 최대 policies.md recent-items.max건 "
		+ "(페이지 없음). 카드는 피드와 같다. 기록은 회원당 최신 recent-items.max건만 남기며, 삭제·숨김 물품은 응답에서 빼므로 그보다 적을 수 있다. "
		+ "본인 물품 조회는 기록하지 않는다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping("/api/members/me/recent-items")
	public ApiResponse<List<ItemSummaryResponse>> getRecentItems(@LoginMember
	Long memberId) {
		return ApiResponse.ok(itemQueryService.getRecentItems(memberId));
	}
}
