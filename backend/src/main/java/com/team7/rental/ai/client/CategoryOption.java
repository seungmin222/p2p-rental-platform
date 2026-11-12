package com.team7.rental.ai.client;

/**
 * AI가 고를 수 있는 카테고리 하나 (REQ-067).
 *
 * @param code categories.code (AI 응답 값)
 * @param name 표시 이름 (모델이 뜻을 알도록 함께 보낸다)
 */
public record CategoryOption(String code, String name) {
}
