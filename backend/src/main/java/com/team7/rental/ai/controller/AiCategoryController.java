package com.team7.rental.ai.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.ai.dto.AiCategoryResponse;
import com.team7.rental.ai.service.AiCategoryService;
import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Item", description = "물품·피드·검색·카테고리")
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class AiCategoryController {

	private final AiCategoryService aiCategoryService;

	@Operation(summary = "AI 카테고리 추천 (REQ-067)", description = "물품 사진을 보고 활성 카테고리 중 하나를 추천한다. 참고용이며 사진은 저장하지 않는다"
		+ "(등록은 POST /api/items로 사진을 다시 보낸다). multipart: images 파일들, 장수·형식·용량은 policies.md item.images. "
		+ "사진 오류만 400이고, AI를 쓸 수 없으면 200에 status FAILED(키 없음·호출 실패·시간 초과 ai.category.timeout-seconds) 또는 "
		+ "LIMIT_EXCEEDED(월 한도 ai.monthly-limit, 회원당 하루 ai.category.per-member-daily)와 null 필드를 준다. "
		+ "맞는 카테고리가 없으면 ETC와 새 카테고리 후보 이름(suggestedCategoryName)을 주고 후보를 관리자 검토용으로 기록한다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED})
	@PostMapping(value = "/ai-category", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<AiCategoryResponse> recommendCategory(@LoginMember
	Long memberId, @RequestPart(value = "images", required = false)
	List<MultipartFile> images) {
		return ApiResponse.ok(aiCategoryService.recommend(memberId, images));
	}
}
