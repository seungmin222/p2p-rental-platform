package com.team7.rental.item.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.dto.CategoryResponse;

import lombok.RequiredArgsConstructor;

/**
 * 카테고리 조회 (REQ-006). 관리(REQ-057)는 3순위. 미구현: 응답 형태만 확정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryService {

	/** 활성 카테고리, sortOrder 순. 애플리케이션 캐시 */
	public List<CategoryResponse> getActiveCategories() {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}
}
