package com.team7.rental.global.response;

/**
 * 필드 검증 오류 하나. 예: { "field": "email", "reason": "올바른 형식의 이메일 주소여야 합니다" }
 */
public record FieldErrorDetail(String field, String reason) {
}
