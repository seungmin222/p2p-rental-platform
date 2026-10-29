package com.team7.rental.item.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.CursorResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.dto.ItemDepositGuideRequest;
import com.team7.rental.item.dto.ItemDepositGuideResponse;
import com.team7.rental.item.dto.ItemDetailResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemFeedRequest;
import com.team7.rental.item.dto.ItemSearchRequest;
import com.team7.rental.item.dto.ItemSummaryResponse;
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.item.service.ItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Item", description = "물품·피드·검색·카테고리")
@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

	private static final String REGION_NOTE = "regionId를 생략하면 로그인 회원의 대표 동네를 쓰고, 비회원이거나 대표 동네가 없으면 400 COMMON_INVALID_INPUT(details regionId). "
		+ "range는 받지만 동네 범위 설정(REQ-042) 전에는 NORMAL로 처리한다(거리 policies.md region.range). "
		+ "물품 거래 장소 좌표가 기준 동네 중심에서 범위 안인 AVAILABLE 물품만(HIDDEN·DELETED 제외). ";

	private final ItemService itemService;
	private final ItemQueryService itemQueryService;

	@Operation(summary = "물품 등록 (REQ-006)", description = "미구현: 응답 형태만 확정. 대표 동네가 인증된(유효기간 내) 회원만 등록할 수 있다(403 REGION_NOT_VERIFIED). "
		+ "물품 동네 = 대표 동네, status = AVAILABLE. "
		+ "multipart: request part(Content-Type application/json, 필수) ItemCreateRequest + images 파일들(순서 = sortOrder, 0번이 대표). "
		+ "사진 장수·형식·용량 policies.md item.images. 비활성·없는 카테고리는 400 COMMON_INVALID_INPUT(details categoryId). "
		+ "검사 순서: request part 검증 400 COMMON_INVALID_INPUT(part의 Content-Type이 json이 아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE) → "
		+ "403 REGION_NOT_VERIFIED → 사진 400(IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, REGION_NOT_VERIFIED,
		IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED})
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ItemCreateResponse> createItem(@LoginMember
	Long memberId,
		@Valid @RequestPart("request")
		ItemCreateRequest request,
		@RequestPart(value = "images", required = false)
		List<MultipartFile> images) {
		return ApiResponse.ok(itemService.create(memberId, request, images));
	}

	@Operation(summary = "권장 보증금 범위 (REQ-006)", description = "미구현: 응답 형태만 확정. 물품 가격 × 카테고리별 권장 비율(policies.md §4). "
		+ "안내만 하고, 범위를 벗어난 보증금도 등록할 수 있다. 없는 카테고리는 400 COMMON_INVALID_INPUT(details categoryId).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED})
	@GetMapping("/deposit-guide")
	public ApiResponse<ItemDepositGuideResponse> getDepositGuide(@ParameterObject @Valid @ModelAttribute
	ItemDepositGuideRequest request) {
		return ApiResponse.ok(itemQueryService.getDepositGuide(request));
	}

	@Operation(summary = "물품 수정 (REQ-007)", description = "미구현: 응답 형태만 확정. 등록자만(403 ITEM_NOT_OWNER). 보낸 항목만 바꾼다. "
		+ "multipart: request part(Content-Type application/json, 필수) ItemUpdateRequest + images 파일들(새 이미지만). "
		+ "이미지: imageOrder가 새 이미지 목록 전체다. 각 칸은 {imageId}(유지할 기존 이미지) 또는 {newImageIndex}(images 파트의 0부터 순번) 중 하나, "
		+ "목록 순서 = sortOrder, 목록에 없는 기존 이미지는 삭제. imageOrder가 null이면 이미지를 바꾸지 않으며 이때 images를 보내면 400 COMMON_INVALID_INPUT. "
		+ "imageOrder가 가리키지 않는 파일이 있거나 없는 newImageIndex·남의 imageId도 400 COMMON_INVALID_INPUT(details imageOrder). 최종 장수 policies.md item.images(어기면 IMAGE_COUNT_INVALID). "
		+ "대여 가능 기간은 보낸 목록으로 교체하며, 대여 예정·진행 중 예약(PENDING_PAYMENT 만료 전·RESERVED·RENTING·OVERDUE)이 차지한 날짜를 빼면 "
		+ "409 ITEM_PERIOD_CONFLICT이고 error.details에 충돌 기간을 한 건씩 담는다: [{ field: \"availablePeriods\", reason: \"2026-11-03 ~ 2026-11-05\" }]. "
		+ "기간 검사는 예약과 같은 물품 락 안에서 하므로 락을 못 잡으면 409 RENTAL_LOCK_TIMEOUT. 가격·보증금 변경은 이후 신규 예약부터 적용된다. "
		+ "검사 순서: request part 검증 400 → 404 ITEM_NOT_FOUND → 403 ITEM_NOT_OWNER → 이미지 400 → 409. 응답은 수정 화면 조회와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND,
		ITEM_NOT_OWNER, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED, ITEM_PERIOD_CONFLICT,
		RENTAL_LOCK_TIMEOUT})
	@PatchMapping(value = "/{itemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ItemEditResponse> updateItem(@LoginMember
	Long memberId, @PathVariable
	Long itemId,
		@Valid @RequestPart("request")
		ItemUpdateRequest request,
		@RequestPart(value = "images", required = false)
		List<MultipartFile> images) {
		return ApiResponse.ok(itemService.update(itemId, memberId, request, images));
	}

	@Operation(summary = "물품 삭제 (REQ-008)", description = "미구현: 응답 형태만 확정. 등록자만. 소프트 삭제(status = DELETED)라 과거 대여·채팅에는 \"삭제된 물품\"으로 남는다. "
		+ "PENDING_PAYMENT·RESERVED·RENTING·OVERDUE·RETURNED 대여가 있으면 409 ITEM_HAS_ACTIVE_RENTAL이고 error.details에 해당 건을 한 건씩 담는다: "
		+ "[{ field: \"rentals\", reason: \"2026-11-03 ~ 2026-11-05 대여 예정 (rentalId 12)\" }]. "
		+ "검사와 삭제는 예약과 같은 물품 락 안에서 하므로 락을 못 잡으면 409 RENTAL_LOCK_TIMEOUT. data는 null.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, ITEM_NOT_FOUND, ITEM_NOT_OWNER, ITEM_HAS_ACTIVE_RENTAL,
		RENTAL_LOCK_TIMEOUT})
	@DeleteMapping("/{itemId}")
	public ApiResponse<Void> deleteItem(@LoginMember
	Long memberId, @PathVariable
	Long itemId) {
		itemService.delete(itemId, memberId);
		return ApiResponse.ok();
	}

	@Operation(summary = "동네 피드 (REQ-009)", description = "미구현: 응답 형태만 확정. " + REGION_NOTE
		+ "정렬 bumpedAt 내림차순, id 내림차순. 커서 기반이고 한 번에 policies.md feed.page건. 결과가 없으면 빈 목록. "
		+ "로그인하지 않아도 되며, 토큰을 보내면 대표 동네 기본값에 쓴다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT})
	@GetMapping("/feed")
	public ApiResponse<CursorResponse<ItemSummaryResponse>> getFeed(@LoginMember(required = false)
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		ItemFeedRequest request) {
		return ApiResponse.ok(itemQueryService.getFeed(request, memberId));
	}

	@Operation(summary = "물품 검색 (REQ-010)", description = "미구현: 응답 형태만 확정. " + REGION_NOTE
		+ "페이지 크기는 policies.md search.page로 고정(size를 받지 않음). 시작일·종료일은 둘 다 보내거나 둘 다 생략하고, 시작일 > 종료일이면 400(details endDate). minPrice > maxPrice면 400(details minPrice). keyword 길이는 제한하지 않는다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT})
	@GetMapping
	public ApiResponse<PageResponse<ItemSummaryResponse>> searchItems(@LoginMember(required = false)
	Long memberId,
		@ParameterObject @Valid @ModelAttribute
		ItemSearchRequest request) {
		return ApiResponse.ok(itemQueryService.search(request, memberId));
	}

	@Operation(summary = "물품 상세 (REQ-011)", description = "미구현: 응답 형태만 확정. 비회원도 볼 수 있다. 없거나 삭제된 물품은 404, 숨김 물품은 등록자만 볼 수 있다(그 외 404). "
		+ "조회 수는 (회원 또는 IP, 물품)당 policies.md view-count.dedupe에 한 번 오른다. isWished는 관심 기능(REQ-022) 전에는 항상 false.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, ITEM_NOT_FOUND})
	@GetMapping("/{itemId}")
	public ApiResponse<ItemDetailResponse> getItem(@LoginMember(required = false)
	Long memberId, @PathVariable
	Long itemId) {
		return ApiResponse.ok(itemQueryService.getDetail(itemId, memberId));
	}
}
