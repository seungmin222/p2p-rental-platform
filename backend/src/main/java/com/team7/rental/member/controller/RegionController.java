package com.team7.rental.member.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.member.dto.RegionCoordRequest;
import com.team7.rental.member.dto.RegionResponse;
import com.team7.rental.member.service.RegionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

	private final RegionService regionService;

	@Operation(summary = "동네 검색 (REQ-004)", description = "행정동 이름에 keyword가 들어간 행정동. 결과가 없으면 빈 배열.")
	@ApiErrorCodes({COMMON_INVALID_INPUT})
	@GetMapping
	public ApiResponse<List<RegionResponse>> searchRegions(
		@Parameter(description = "검색어 (행정동 이름 일부)", example = "송도") @RequestParam @NotBlank
		String keyword) {
		return ApiResponse.ok(regionService.search(keyword));
	}

	@Operation(summary = "좌표로 행정동 찾기 (REQ-004)", description = "브라우저 현재 위치를 행정동으로 바꾼다(서버가 카카오 로컬 API 호출). "
		+ "좌표는 저장하지 않는다. 해당 행정동이 없으면 404 COMMON_NOT_FOUND.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_NOT_FOUND})
	@GetMapping("/by-coord")
	public ApiResponse<RegionResponse> getRegionByCoord(@ParameterObject @Valid @ModelAttribute
	RegionCoordRequest coord) {
		return ApiResponse.ok(regionService.getByCoord(coord));
	}
}
