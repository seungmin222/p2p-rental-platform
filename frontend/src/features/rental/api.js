// 대여 API 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/**
 * multipart 본문: `request` 파트(JSON) + `images` 파트 반복.
 * request 파트는 Content-Type이 application/json이어야 한다(아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE).
 * images는 ImageUploader value에서 꺼낸 File 배열.
 */
function toMultipart(request, images = []) {
  const form = new FormData();
  form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  for (const file of images) {
    form.append('images', file);
  }
  return form;
}

/** GET /api/items/{itemId}/calendar → RentalCalendarResponse */
export function fetchCalendar(itemId, month) {
  return apiClient.get(`/items/${itemId}/calendar`, { params: { month } });
}

/** POST /api/rentals { itemId, startDate, endDate } → RentalDetailResponse */
export function createRental(body) {
  return apiClient.post('/rentals', body);
}

/** GET /api/rentals/{rentalId} → RentalDetailResponse */
export function fetchRental(rentalId) {
  return apiClient.get(`/rentals/${rentalId}`);
}

/** GET /api/rentals/{rentalId}/inspections → RentalInspectionResponse[] */
export function fetchInspections(rentalId) {
  return apiClient.get(`/rentals/${rentalId}/inspections`);
}

/** PATCH /api/rentals/{rentalId}/cancel { reason, reasonText? } → RentalDetailResponse */
export function cancelRental(rentalId, body) {
  return apiClient.patch(`/rentals/${rentalId}/cancel`, body);
}

/** POST /api/rentals/{rentalId}/cancel-agreements { reason, reasonText? } → CancelAgreementResponse */
export function requestCancelAgreement(rentalId, body) {
  return apiClient.post(`/rentals/${rentalId}/cancel-agreements`, body);
}

/** PATCH /api/rentals/{rentalId}/cancel-agreements/{agreementId} { action } → RentalDetailResponse */
export function respondCancelAgreement(rentalId, agreementId, action) {
  return apiClient.patch(`/rentals/${rentalId}/cancel-agreements/${agreementId}`, { action });
}

/** PATCH /api/rentals/{rentalId}/handover (multipart: request { memo }, images) → RentalDetailResponse */
export function handoverRental(rentalId, request, images) {
  return apiClient.patch(`/rentals/${rentalId}/handover`, toMultipart(request, images));
}

/**
 * POST /api/rentals/{rentalId}/return-request (multipart: request { checklist, note }, images)
 * → RentalDetailResponse. checklist는 ReturnChecklistItem 5개 키 전부의 맵.
 */
export function requestReturn(rentalId, request, images) {
  return apiClient.post(`/rentals/${rentalId}/return-request`, toMultipart(request, images));
}

/**
 * PATCH /api/rentals/{rentalId}/return (multipart: request { result, resultNote }, images)
 * → RentalDetailResponse. NORMAL이면 images를 보내지 않는다.
 */
export function confirmReturn(rentalId, request, images) {
  return apiClient.patch(`/rentals/${rentalId}/return`, toMultipart(request, images));
}

/**
 * GET /api/members/me/rentals?role=renter&tab=&page= → PageResponse<RentalHistoryResponse>.
 * size는 보내지 않는다(서버가 rental-history.page로 고정). 정렬도 서버가 한다.
 */
export function fetchRentalHistory({ tab, page }) {
  return apiClient.get('/members/me/rentals', { params: { role: 'renter', tab, page } });
}

// TODO: 백엔드 확정 전 임시 — 물품별 예약 현황(REQ-020)은 openapi.json에 아직 없다. 빌린 내역과 같은 page 형식,
// 항목 { rentalId, startDate, endDate, status, renter { memberId, nickname } }으로 가정했다. 응답 항목을 읽는 곳은
// toItemRental 하나다. 머지되면 openapi.json으로 맞춘다.
/** GET /api/items/{itemId}/rentals?page= (제공자 본인만) */
export function fetchItemRentals(itemId, page) {
  return apiClient.get(`/items/${itemId}/rentals`, { params: { page } });
}

// TODO: 백엔드 확정 전 임시 — 위 fetchItemRentals 항목의 가정한 필드
/** 예약 현황 시트 한 줄 */
export function toItemRental(rental) {
  return {
    rentalId: rental.rentalId,
    startDate: rental.startDate,
    endDate: rental.endDate,
    status: rental.status,
    renterNickname: rental.renter?.nickname ?? '',
  };
}
