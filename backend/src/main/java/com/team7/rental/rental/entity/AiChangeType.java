package com.team7.rental.rental.entity;

/** AI 비교 출력의 변화 유형 (docs/domains/ai.md REQ-040 출력 스키마 changeTypes 허용 값) */
public enum AiChangeType {
	SCRATCH,
	STAIN,
	MISSING_PART,
	BROKEN,
	OTHER
}
