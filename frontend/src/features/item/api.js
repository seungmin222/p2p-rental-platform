import { apiClient, toMultipart } from '../../shared/api';

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

/**
 * 피드·검색 카드(openapi.json ItemSummaryResponse 기준, 화면이 쓰는 필드만).
 * 관심·채팅 수는 홈 카드에 숫자로 표시만 하므로 옮긴다(누르는 관심·채팅 UI는 REQ-022·026, 2순위).
 * 상세(toItemDetail)는 화면에 수를 그리지 않아 옮기지 않는다.
 */
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
    wishCount: item.wishCount ?? 0,
    chatCount: item.chatCount ?? 0,
  };
}

/**
 * 상세 화면(openapi.json ItemDetailResponse 기준, 화면이 쓰는 필드만). 조회·관심·채팅 수는 쓰지 않는다. 좌표는 지도용(REQ-030).
 * isWished는 하단 바 하트(REQ-022). 비회원·제공자 본인은 서버가 false로 준다
 */
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
    latitude: item.latitude ?? null,
    longitude: item.longitude ?? null,
    regionName: item.regionName ?? '',
    bumpedAt: item.bumpedAt,
    owner: {
      memberId: item.owner?.memberId,
      nickname: item.owner?.nickname ?? '',
      profileImageUrl: item.owner?.profileImageUrl ?? null,
      regionName: item.owner?.regionName ?? '',
    },
    isOwner: item.isOwner === true,
    isWished: item.isWished === true,
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

/** POST /api/items (multipart: request ItemCreateRequest, images) → ItemCreateResponse { itemId } */
export function createItem({ request, images }) {
  return apiClient.post('/items', toMultipart(request, images));
}

/** GET /api/members/me/items/{itemId} → ItemEditResponse(소유자 전용, itemPrice·좌표 포함) */
export function fetchItemForEdit(itemId) {
  return apiClient.get(`/members/me/items/${itemId}`);
}

/** PATCH /api/items/{itemId} (multipart: request ItemUpdateRequest, images 새 파일만) → ItemEditResponse */
export function updateItem(itemId, { request, images }) {
  return apiClient.patch(`/items/${itemId}`, toMultipart(request, images));
}

/** DELETE /api/items/{itemId} → data null(소프트 삭제) */
export function deleteItem(itemId) {
  return apiClient.delete(`/items/${itemId}`);
}

/**
 * POST /api/items/ai-draft (REQ-067, multipart images만) → AiDraftResponse. 사진은 저장하지 않는다.
 * AI를 쓸 수 없으면 200에 status FAILED·LIMIT_EXCEEDED, 사진 오류만 400
 * @param {File[]} images
 */
export function draftItem(images) {
  const form = new FormData();
  for (const file of images) {
    form.append('images', file);
  }
  return apiClient.post('/items/ai-draft', form);
}

/** GET /api/items/deposit-guide →ItemDepositGuideResponse { minDeposit, maxDeposit }. 로그인 필요 */
export function fetchDepositGuide({ categoryId, itemPrice }) {
  return apiClient.get('/items/deposit-guide', { params: { categoryId, itemPrice } });
}

/** POST /api/items/{itemId}/wishes → WishResponse { itemId, isWished, wishCount }. 멱등(이미 관심이어도 200) */
export function addWish(itemId) {
  return apiClient.post(`/items/${itemId}/wishes`);
}

/** DELETE /api/items/{itemId}/wishes → WishResponse. 멱등. 삭제·숨김 물품도 해제할 수 있다 */
export function removeWish(itemId) {
  return apiClient.delete(`/items/${itemId}/wishes`);
}

/** GET /api/members/me/wishes?page= → PageResponse<WishItemResponse>. size는 보내지 않는다(서버가 wish.page로 고정) */
export function fetchMyWishes(page) {
  return apiClient.get('/members/me/wishes', { params: { page } });
}

/** GET /api/members/me/recent-items → ItemSummaryResponse[](최대 recent-items.max건, 페이지 없음) */
export function fetchRecentItems() {
  return apiClient.get('/members/me/recent-items');
}

/**
 * 관심 목록 카드(openapi.json WishItemResponse 기준). 등록 시각(bumpedAt)이 없어 카드에 경과 시간이 없다.
 * DELETED·HIDDEN이면 itemId·status·wishedAt 외 필드가 비어 있어 isUnavailable로 따로 그린다.
 */
export function toWishItem(item) {
  return {
    itemId: item.itemId,
    status: item.status,
    isUnavailable: item.status === 'DELETED' || item.status === 'HIDDEN',
    name: item.name ?? '',
    thumbnailUrl: item.thumbnailUrl ?? null,
    regionName: item.regionName ?? '',
    bumpedAt: null,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    isRenting: item.isRenting === true,
    wishCount: item.wishCount ?? 0,
    chatCount: item.chatCount ?? 0,
  };
}

/** ItemEditResponse → 폼 값(itemForm.js). 금액 칸은 문자열, 사진은 ImageUploader value 모양 */
export function toItemEditForm(item) {
  return {
    name: item.name ?? '',
    description: item.description ?? '',
    images: (item.images ?? []).map((image) => ({ id: image.imageId, url: image.url })),
    categoryId: item.categoryId,
    condition: item.condition,
    pricePerDay: String(item.pricePerDay),
    itemPrice: String(item.itemPrice),
    deposit: String(item.deposit),
    availablePeriods: (item.availablePeriods ?? []).map(({ startDate, endDate }) => ({
      startDate,
      endDate,
    })),
    address: item.address ?? '',
    latitude: item.latitude,
    longitude: item.longitude,
  };
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
