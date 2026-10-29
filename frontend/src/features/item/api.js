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
