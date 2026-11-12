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

	/** AI 카테고리 추천(REQ-067)의 선택지. code를 응답에 쓰므로 화면용 목록과 따로 둔다. 관리자 변경이 바로 보이도록 캐시하지 않는다 */
	public List<CategoryCode> getActiveCategoryCodes() {
		return categories.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
			.map(c -> new CategoryCode(c.getId(), c.getCode(), c.getName())).toList();
	}

	public record CategoryCode(Long id, String code, String name) {
	}

	/** 관리자 카테고리 변경(REQ-057) 구현 시 커밋 후 호출한다. */
	public synchronized void evictCache() {
		cachedCategories = null;
	}
}
