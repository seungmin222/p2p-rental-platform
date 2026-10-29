import { apiClient } from '../../shared/api';

/** GET /api/items/{itemId} (REQ-011) */
export function fetchItem(itemId) {
  return apiClient.get(`/items/${itemId}`);
}

/** 예약 화면 상단·본인 물품 여부에 쓰는 요약(openapi.json ItemDetailResponse 기준, 화면이 쓰는 필드만) */
export function toItemSummary(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.images?.[0]?.thumbnailUrl ?? null,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    ownerNickname: item.owner?.nickname ?? '',
    regionName: item.owner?.regionName ?? '',
    isOwner: item.isOwner === true,
  };
}

/**
 * GET /api/members/me/items?tab=AVAILABLE|RENTING&page= → PageResponse<MyItemResponse>.
 * HIDDEN 탭은 3순위라 부르지 않는다. size는 보내지 않는다(서버가 my-items.page로 고정). 정렬도 서버가 한다.
 */
export function fetchMyItems({ tab, page }) {
  return apiClient.get('/members/me/items', { params: { tab, page } });
}

/** 내 물품 관리 카드(openapi.json MyItemResponse 기준, 화면이 쓰는 필드만) */
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
