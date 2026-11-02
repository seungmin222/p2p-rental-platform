package com.team7.rental.item.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.item.dto.CategoryResponse;
import com.team7.rental.item.entity.Category;
import com.team7.rental.item.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 카테고리 조회 (REQ-006). 관리(REQ-057)는 3순위.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryService {
	private final CategoryRepository categories;
	private volatile List<CategoryResponse> cachedCategories;

	public Category requireActive(Long categoryId) {
		return categories.findById(categoryId).filter(Category::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("categoryId", "활성 카테고리를 선택해 주세요."))));
	}

	/** 활성 카테고리, sortOrder 순. */
	public List<CategoryResponse> getActiveCategories() {
		var cached = cachedCategories;
		if (cached != null) {
			return cached;
		}
		return loadCategories();
	}

	private synchronized List<CategoryResponse> loadCategories() {
		if (cachedCategories == null) {
			cachedCategories = categories.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
				.map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSortOrder())).toList();
		}
		return cachedCategories;
	}

	/** 관리자 카테고리 변경(REQ-057) 구현 시 커밋 후 호출한다. */
	public synchronized void evictCache() {
		cachedCategories = null;
	}
}
