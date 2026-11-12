package com.team7.rental.item.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.entity.CategorySuggestion;
import com.team7.rental.item.repository.CategorySuggestionRepository;

import lombok.RequiredArgsConstructor;

/** 카테고리 후보 기록 (REQ-067). 검토는 관리자 기능(REQ-057) */
@Service
@RequiredArgsConstructor
public class CategorySuggestionService {

	private final CategorySuggestionRepository categorySuggestions;

	/**
	 * AI 추천이 ETC를 고를 때 후보 이름을 PENDING으로 남긴다. 같은 이름도 요청마다 한 행이다(검토 화면에서 묶는다).
	 *
	 * @param suggestedName 공백을 정리하고 categories.name 길이(30자) 안으로 자른 이름
	 */
	@Transactional
	public void recordFromAi(Long memberId, String suggestedName) {
		categorySuggestions.save(CategorySuggestion.pendingFromAi(memberId, suggestedName));
	}
}
