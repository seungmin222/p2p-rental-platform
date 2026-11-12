package com.team7.rental.ai.dto;

/** AI 카테고리 추천 결과 (REQ-067). DONE이 아니면 추천 필드가 모두 null이고 사용자가 직접 고른다 */
public enum AiCategoryStatus {
	/** 추천 성공 */
	DONE,
	/** 키 없음·호출 실패·시간 초과·형식 오류(재시도 후) */
	FAILED,
	/** 월 한도(ai.monthly-limit) 또는 회원당 하루 횟수(ai.category.per-member-daily) 초과 */
	LIMIT_EXCEEDED
}
