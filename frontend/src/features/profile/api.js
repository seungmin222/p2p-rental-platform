// 사용자 프로필 API(REQ-021). 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
// 비회원도 볼 수 있지만 공개 API 목록에 넣지 않는다 — 로그인했으면 토큰을 보내 둔다(차단 관계 REQ-056 확장 지점, 물품 상세와 같은 이유).
import { apiClient } from '../../shared/api';

/** GET /api/members/{memberId} → MemberProfileResponse. 탈퇴 회원은 isWithdrawn만 true, 나머지 null */
export function fetchMemberProfile(memberId) {
  return apiClient.get(`/members/${memberId}`);
}

/**
 * GET /api/members/{memberId}/items?page= → PageResponse<ItemSummaryResponse>(피드 카드와 같은 모양).
 * size는 보내지 않는다(서버가 profile-items.page로 고정). 정렬(bumpedAt·id 내림차순)도 서버가 한다.
 */
export function fetchMemberItems(memberId, page) {
  return apiClient.get(`/members/${memberId}/items`, { params: { page } });
}
