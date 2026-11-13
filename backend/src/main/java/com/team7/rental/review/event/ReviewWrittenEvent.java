package com.team7.rental.review.event;

/**
 * 후기가 저장됐다 (REQ-034). 커밋 뒤 상대에게 REVIEW_RECEIVED 알림을 보낸다. 신뢰 점수(REQ-054)를 구현하면 이 이벤트를 받아
 * trust_score_events를 쌓는다(source_id = reviewId, 만족도·칭찬 항목은 reviews·review_compliments에서 읽는다).
 *
 * @param targetId 후기를 받은 회원
 */
public record ReviewWrittenEvent(Long reviewId, Long rentalId, Long writerId, Long targetId) {
}
