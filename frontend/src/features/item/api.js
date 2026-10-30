import { apiClient } from '../../shared/api';

/** GET /api/items/{itemId} (REQ-011). 토큰을 보내야 isOwner가 로그인 사용자 기준이다(공개 API 목록에 넣지 않음) */
export function fetchItem(itemId) {
  return apiClient.get(`/items/${itemId}`);
}

/** GET /api/categories → CategoryResponse[] ({ categoryId, name, sortOrder }, sortOrder 순). 공개 API */
export function fetchCategories() {
  return apiClient.get('/categories');
}

/**
 * GET /api/items/feed → CursorResponse<ItemSummaryResponse> ({ items, hasNext, nextCursor }). 공개 API.
 * regionId는 항상 보낸다(생략하면 서버가 로그인 회원의 대표 동네를 쓰는데 공개라 토큰을 보내지 않는다). range는 보내지 않는다(REQ-042 전 NORMAL).
 */
export function fetchFeed({ regionId, categoryId, cursor }) {
  return apiClient.get('/items/feed', { params: { regionId, categoryId, cursor } });
}

/** GET /api/items?...&page= → PageResponse<ItemSummaryResponse>. 공개 API. params는 searchParams.js toApiParams 결과 + page */
export function searchItems(params) {
  return apiClient.get('/items', { params });
}

/** 피드·검색 카드(openapi.json ItemSummaryResponse 기준, 화면이 쓰는 필드만). 관심·채팅 수는 2순위라 옮기지 않는다 */
export function toItemCard(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl ?? null,
    regionName: item.regionName ?? '',
    bumpedAt: item.bumpedAt,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    isRenting: item.isRenting === true,
  };
}

/** 상세 화면(openapi.json ItemDetailResponse 기준, 화면이 쓰는 필드만). 조회·관심·채팅 수, 좌표는 쓰지 않는다 */
export function toItemDetail(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    images: (item.images ?? []).map((image) => ({ imageId: image.imageId, url: image.url })),
    categoryName: item.category?.name ?? '',
    condition: item.condition,
    description: item.description ?? '',
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    address: item.address ?? '',
    regionName: item.regionName ?? '',
    bumpedAt: item.bumpedAt,
    owner: {
      memberId: item.owner?.memberId,
      nickname: item.owner?.nickname ?? '',
      profileImageUrl: item.owner?.profileImageUrl ?? null,
      regionName: item.owner?.regionName ?? '',
    },
    isOwner: item.isOwner === true,
  };
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
