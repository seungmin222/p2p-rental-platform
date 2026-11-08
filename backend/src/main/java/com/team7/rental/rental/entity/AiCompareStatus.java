package com.team7.rental.rental.entity;

/**
 * AI 상태 비교 진행 상태 (REQ-040, item_inspections.ai_result.status). 결과가 없는 경우를 화면이 구분해 보여 줄 수 있게 나눈다.
 * 컬럼이 NULL이면 아직 비교하지 않은 것이다(응답 aiResult = null).
 */
public enum AiCompareStatus {
	/** 워커가 맡아 비교 중. ai.compare.processing-timeout-minutes가 지나면 응답에서 FAILED로 보인다(open-issues #46) */
	PROCESSING,
	/** 비교 완료. result가 있다 */
	DONE,
	/** 호출·응답 형식 오류(재시도 소진)·사진 읽기 실패 */
	FAILED,
	/** 월 호출 한도(ai.monthly-limit) 초과로 호출하지 않음 */
	LIMIT_EXCEEDED
}
