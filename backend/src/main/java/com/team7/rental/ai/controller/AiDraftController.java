package com.team7.rental.ai.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.ai.dto.AiDraftResponse;
import com.team7.rental.ai.service.AiDraftService;
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
public class AiDraftController {

	private final AiDraftService aiDraftService;

	@Operation(summary = "AI 등록 보조 (REQ-067)", description = "물품 사진을 보고 등록 폼에 채울 초안을 만든다: 활성 카테고리 중 하나(categoryId, "
		+ "맞는 것이 없으면 ETC와 새 카테고리 후보 이름 suggestedCategoryName, 후보는 관리자 검토용으로 기록), 제목·설명 초안, 추정 원가(itemPrice), "
		+ "일 대여료 제안(pricePerDay), 권장 보증금 범위(minDeposit·maxDeposit = 추정 원가에 GET /api/items/deposit-guide와 같은 규칙). "
		+ "참고용이며 사진은 저장하지 않는다(등록은 POST /api/items로 사진을 다시 보낸다). 원가를 고치면 deposit-guide로 다시 계산한다. "
		+ "multipart: images 파일들, 장수·형식·용량은 policies.md item.images. 사진 오류만 400이고, AI를 쓸 수 없으면 200에 status FAILED"
		+ "(키 없음·호출 실패·시간 초과 ai.draft.timeout-seconds·형식 오류) 또는 LIMIT_EXCEEDED(월 한도 ai.monthly-limit, "
		+ "회원당 하루 ai.draft.per-member-daily)와 null 필드를 준다. DONE이면 카테고리는 항상 있고, 제목·설명·원가·대여료는 만들지 못했거나 "
		+ "등록 범위(policies.md item.*)를 벗어나면 그 필드만 null이다(원가가 null이면 보증금 범위도 null).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED})
	@PostMapping(value = "/ai-draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<AiDraftResponse> draft(@LoginMember
	Long memberId, @RequestPart(value = "images", required = false)
	List<MultipartFile> images) {
		return ApiResponse.ok(aiDraftService.draft(memberId, images));
	}
}
