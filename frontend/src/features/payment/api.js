// 결제 API 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/**
 * POST /api/payments { rentalId, extensionId? } → PaymentOrderResponse { orderId, amount, orderName, customerKey }.
 * extensionId가 있으면 그 연장의 연장료 주문(REQ-038), 없으면 대여료 + 보증금 주문
 */
export function createPaymentOrder(rentalId, extensionId) {
  return apiClient.post(
    '/payments',
    extensionId == null ? { rentalId } : { rentalId, extensionId },
  );
}

/** POST /api/payments/confirm { paymentKey, orderId, amount } → PaymentConfirmResponse */
export function confirmPayment(body) {
  return apiClient.post('/payments/confirm', body);
}

/**
 * GET /api/members/me/payments?type=&from=&to=&page= → PageResponse<PaymentHistoryResponse> (REQ-033, 대여자 본인).
 * params는 paymentHistory.js toHistoryParams가 만든다(빈 값은 보내지 않음). size는 보내지 않는다(payment-history.page 고정)
 */
export function fetchPaymentHistory(params) {
  return apiClient.get('/members/me/payments', { params });
}

/** GET /api/members/me/settlements?page= → SettlementListResponse { scheduledTotal, heldTotal, paidTotal, settlements } (REQ-039) */
export function fetchSettlements(page) {
  return apiClient.get('/members/me/settlements', { params: { page } });
}
