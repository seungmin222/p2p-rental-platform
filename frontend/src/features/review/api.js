// 후기 API 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/** POST /api/rentals/{rentalId}/reviews { satisfaction, compliments, content? } → ReviewResponse */
export function writeReview(rentalId, body) {
  return apiClient.post(`/rentals/${rentalId}/reviews`, body);
}

/**
 * GET /api/members/{memberId}/reviews?page= → PageResponse<ReceivedReviewResponse>.
 * size는 보내지 않는다(서버가 review.page로 고정). 최신 작성 순도 서버가 정한다.
 */
export function fetchReceivedReviews(memberId, page) {
  return apiClient.get(`/members/${memberId}/reviews`, { params: { page } });
}
