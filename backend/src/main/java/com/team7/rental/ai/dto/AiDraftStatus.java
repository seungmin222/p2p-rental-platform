package com.team7.rental.ai.dto;

/** AI 등록 보조 결과 (REQ-067). DONE이 아니면 초안 필드가 모두 null이고 사용자가 직접 입력한다 */
public enum AiDraftStatus {
	/** 초안 생성 성공 (카테고리는 항상 있고, 나머지 필드는 하나씩 null일 수 있다) */
	DONE,
	/** 키 없음·호출 실패·시간 초과·형식 오류(재시도 후) */
	FAILED,
	/** 월 한도(ai.monthly-limit) 또는 회원당 하루 횟수(ai.draft.per-member-daily) 초과 */
	LIMIT_EXCEEDED
}
