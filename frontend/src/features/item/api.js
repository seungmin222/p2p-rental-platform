import { apiClient } from '../../shared/api';

/** GET /api/items/{itemId} (REQ-011) */
export function fetchItem(itemId) {
  return apiClient.get(`/items/${itemId}`);
}

// TODO: 백엔드 확정 전 임시 — REQ-011 물품 상세는 openapi.json에 아직 없다(feat/item-crud). 필드명은
// item.md REQ-011 목록에서 가정했고, 응답을 읽는 곳은 이 함수 하나다. 머지되면 openapi.json으로 맞춘다.
/** 예약 화면 상단·본인 물품 여부에 쓰는 요약 */
export function toItemSummary(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.images?.[0]?.url ?? null,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    ownerNickname: item.owner?.nickname ?? '',
    regionName: item.owner?.regionName ?? '',
    isOwner: item.isOwner === true,
  };
}

// TODO: 백엔드 확정 전 임시 — 내 물품 목록(REQ-020)은 openapi.json에 아직 없다. 빌린 내역과 같은 page 형식,
// 항목 { itemId, name, thumbnailUrl, pricePerDay, deposit, viewCount, wishCount, chatCount, upcomingRentalCount }로
// 가정했다(item.md REQ-020 카드 + erd.md items 카운터 이름). 응답 항목을 읽는 곳은 toMyItem 하나다.
/** GET /api/members/me/items?tab=AVAILABLE|RENTING&page= (HIDDEN 탭은 3순위라 부르지 않는다) */
export function fetchMyItems({ tab, page }) {
  return apiClient.get('/members/me/items', { params: { tab, page } });
}

// TODO: 백엔드 확정 전 임시 — 위 fetchMyItems 항목의 가정한 필드
/** 내 물품 관리 카드 */
export function toMyItem(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl ?? null,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    viewCount: item.viewCount ?? 0,
    wishCount: item.wishCount ?? 0,
    chatCount: item.chatCount ?? 0,
    upcomingRentalCount: item.upcomingRentalCount ?? 0,
  };
}
