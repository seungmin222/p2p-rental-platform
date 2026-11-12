package com.team7.rental.item.entity;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 후보 (REQ-067). AI 추천이 ETC를 고를 때 후보 이름을 PENDING으로 남긴다. 추천은 등록 전이라 itemId가 없다(open-issues #52).
 * 검토(승인·반려)는 관리자 기능(REQ-057)에서 메서드를 더한다
 */
@Getter
@Entity
@Table(name = "category_suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategorySuggestion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long itemId;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 30)
	private String suggestedName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CategorySuggestionStatus status;

	private Long reviewedBy;

	/** AI 추천 시점의 후보 (물품 없음, PENDING) */
	public static CategorySuggestion pendingFromAi(Long memberId, String suggestedName) {
		CategorySuggestion suggestion = new CategorySuggestion();
		suggestion.memberId = memberId;
		suggestion.suggestedName = suggestedName;
		suggestion.status = CategorySuggestionStatus.PENDING;
		return suggestion;
	}
}
