package com.team7.rental.rental.event;

/**
 * 반납 요청(REQ-018, 대리 접수 포함)이 저장됐다. 커밋 뒤 AI 비교 큐 적재(REQ-040)가 받는다. 미반납 신고는 사진이 없어 발행하지 않는다.
 */
public record ReturnRequestedEvent(Long rentalId) {
}
