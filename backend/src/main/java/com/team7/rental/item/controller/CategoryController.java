package com.team7.rental.item.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.item.dto.CategoryResponse;
import com.team7.rental.item.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Item", description = "물품·피드·검색·카테고리")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@Operation(summary = "카테고리 목록 (REQ-006)", description = "미구현: 응답 형태만 확정. 활성 카테고리만 sortOrder 순.")
	@ApiErrorCodes({})
	@GetMapping
	public ApiResponse<List<CategoryResponse>> getCategories() {
		return ApiResponse.ok(categoryService.getActiveCategories());
	}
}
