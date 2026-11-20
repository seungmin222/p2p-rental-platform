package com.team7.rental.item.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.item.dto.ItemSummaryResponse;
import com.team7.rental.item.dto.MemberItemListRequest;
import com.team7.rental.item.service.ItemQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 사용자 프로필의 등록 물품. 경로는 회원 아래지만 물품 목록이라 item 도메인에 둔다(내 물품 MyItemController와 같은 방식). */
@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequiredArgsConstructor
public class MemberItemController {

	private final ItemQueryService itemQueryService;

	@Operation(summary = "사용자 프로필 등록 물품 (REQ-021)", description = "비회원도 볼 수 있다. 회원의 AVAILABLE 물품만(HIDDEN·DELETED 제외), "
		+ "정렬은 피드와 같이 bumpedAt 내림차순, id 내림차순. 카드 형태는 피드와 같다. 페이지 크기는 policies.md profile-items.page로 고정(size를 받지 않음). "
		+ "탈퇴 회원은 상세 비공개라 빈 목록이다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, MEMBER_NOT_FOUND})
	@GetMapping("/api/members/{memberId}/items")
	public ApiResponse<PageResponse<ItemSummaryResponse>> getMemberItems(@LoginMember(required = false)
	Long viewerId, @PathVariable
	Long memberId, @ParameterObject @Valid @ModelAttribute
	MemberItemListRequest request) {
		return ApiResponse.ok(itemQueryService.getMemberItems(memberId, viewerId, request.page()));
	}
}
