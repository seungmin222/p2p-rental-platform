// 대여 API 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient, toMultipart } from '../../shared/api';

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
 * POST /api/rentals/{rentalId}/extensions { newEndDate } → RentalExtensionResponse
 * (status PENDING_PAYMENT면 extensionId로 결제, CONFIRMED면 이미 종료일이 바뀜)
 */
export function requestExtension(rentalId, body) {
  return apiClient.post(`/rentals/${rentalId}/extensions`, body);
}

/** POST /api/rentals/{rentalId}/lost-report { note? } → RentalDetailResponse (제공자, REQ-018) */
export function reportLost(rentalId, body) {
  return apiClient.post(`/rentals/${rentalId}/lost-report`, body);
}

/** GET /api/rentals/{rentalId}/deposit/policy?grade= → DeductionPolicyResponse (REQ-049) */
export function fetchDeductionPolicy(rentalId, grade) {
  return apiClient.get(`/rentals/${rentalId}/deposit/policy`, { params: { grade } });
}

/**
 * PATCH /api/rentals/{rentalId}/deposit (multipart) → RentalDetailResponse (REQ-049).
 * request 파트 DepositActionRequest { action, proposalId?, grade?, amount?, reason? } +
 * evidenceImages(손상 사진)·receiptImages(견적서·영수증) 파트. ACCEPT·REJECT는 사진을 보내지 않는다.
 * 사진 파트 이름이 images가 아니라 toMultipart를 쓰지 않는다.
 */
export function actOnDeposit(rentalId, request, { evidenceImages = [], receiptImages = [] } = {}) {
  const form = new FormData();
  form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  for (const file of evidenceImages) {
    form.append('evidenceImages', file);
  }
  for (const file of receiptImages) {
    form.append('receiptImages', file);
  }
  return apiClient.patch(`/rentals/${rentalId}/deposit`, form);
}

/**
 * GET /api/members/me/rentals?role=renter&tab=&page= → PageResponse<RentalHistoryResponse>.
 * size는 보내지 않는다(서버가 rental-history.page로 고정). 정렬도 서버가 한다.
 */
export function fetchRentalHistory({ tab, page }) {
  return apiClient.get('/members/me/rentals', { params: { role: 'renter', tab, page } });
}

/**
 * GET /api/items/{itemId}/rentals?page= → PageResponse<ItemRentalResponse> (제공자 본인만, 남의 물품 403 ITEM_NOT_OWNER).
 * size는 보내지 않는다(서버가 item-rentals.page로 고정). 정렬도 서버가 한다.
 */
export function fetchItemRentals(itemId, page) {
  return apiClient.get(`/items/${itemId}/rentals`, { params: { page } });
}

/** 예약 현황 시트 한 줄(openapi.json ItemRentalResponse 기준) */
export function toItemRental(rental) {
  return {
    rentalId: rental.rentalId,
    startDate: rental.startDate,
    endDate: rental.endDate,
    status: rental.status,
    renterNickname: rental.renter?.nickname ?? '',
  };
}
