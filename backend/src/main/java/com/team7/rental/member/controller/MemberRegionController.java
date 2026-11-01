package com.team7.rental.member.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.team7.rental.member.dto.MemberRegionAddRequest;
import com.team7.rental.member.dto.MemberRegionResponse;
import com.team7.rental.member.dto.RegionCoordRequest;
import com.team7.rental.member.service.MemberRegionQueryService;
import com.team7.rental.member.service.MemberRegionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 경로의 {memberRegionId}가 남의 동네이면 없는 것과 같게 404 COMMON_NOT_FOUND */
@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequestMapping("/api/members/me/regions")
@RequiredArgsConstructor
public class MemberRegionController {

	private final MemberRegionService memberRegionService;
	private final MemberRegionQueryService memberRegionQueryService;

	@Operation(summary = "내 동네 목록 (REQ-004)", description = "대표 동네 먼저. 최대 개수는 policies.md region.max-per-member.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping
	public ApiResponse<List<MemberRegionResponse>> getMyRegions(@LoginMember
	Long memberId) {
		return ApiResponse.ok(memberRegionQueryService.getMyRegions(memberId));
	}

	@Operation(summary = "동네 추가 (REQ-004)", description = "인증 전 상태로 추가된다(인증은 verify). 첫 동네면 자동으로 대표 동네. "
		+ "개수가 policies.md region.max-per-member를 넘으면 409 REGION_LIMIT_EXCEEDED, 이미 추가한 동네면 409 REGION_ALREADY_ADDED, "
		+ "요청 바디의 regionId가 없는 행정동이면 400 COMMON_INVALID_INPUT(details regionId). 404는 경로 id가 없을 때만 쓴다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, REGION_LIMIT_EXCEEDED, REGION_ALREADY_ADDED})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MemberRegionResponse> addRegion(@LoginMember
	Long memberId,
		@Valid @RequestBody
		MemberRegionAddRequest request) {
		return ApiResponse.ok(memberRegionService.add(memberId, request));
	}

	@Operation(summary = "동네 인증 (REQ-004)", description = "브라우저 현재 좌표의 행정동이 이 동네와 같으면 인증된다(verifiedAt = 지금, "
		+ "유효기간 policies.md region.verify-valid-days). 좌표는 저장하지 않는다. 다르면 400 REGION_VERIFY_MISMATCH. "
		+ "위치 권한이 없으면 프론트가 이 API를 부르지 않고 미인증 상태로 둔다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, COMMON_NOT_FOUND, REGION_VERIFY_MISMATCH})
	@PatchMapping("/{memberRegionId}/verify")
	public ApiResponse<MemberRegionResponse> verifyRegion(@LoginMember
	Long memberId, @PathVariable
	Long memberRegionId,
		@Valid @RequestBody
		RegionCoordRequest request) {
		return ApiResponse.ok(memberRegionService.verify(memberId, memberRegionId, request));
	}

	@Operation(summary = "대표 동네 설정 (REQ-004)", description = "두 동네의 isPrimary가 함께 바뀌므로 바뀐 뒤의 내 동네 목록 전체를 돌려준다(대표 동네 먼저).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, COMMON_NOT_FOUND})
	@PatchMapping("/{memberRegionId}/primary")
	public ApiResponse<List<MemberRegionResponse>> changePrimaryRegion(@LoginMember
	Long memberId, @PathVariable
	Long memberRegionId) {
		return ApiResponse.ok(memberRegionService.changePrimary(memberId, memberRegionId));
	}

	@Operation(summary = "동네 삭제 (REQ-004)", description = "대표 동네를 지우면 남은 동네가 대표가 된다. data는 null.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, COMMON_NOT_FOUND})
	@DeleteMapping("/{memberRegionId}")
	public ApiResponse<Void> deleteRegion(@LoginMember
	Long memberId, @PathVariable
	Long memberRegionId) {
		memberRegionService.delete(memberId, memberRegionId);
		return ApiResponse.ok();
	}
}
