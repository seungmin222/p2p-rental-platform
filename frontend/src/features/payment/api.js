// 결제 API 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/** POST /api/payments { rentalId } → PaymentOrderResponse { orderId, amount, orderName, customerKey } */
export function createPaymentOrder(rentalId) {
  return apiClient.post('/payments', { rentalId });
}

/** POST /api/payments/confirm { paymentKey, orderId, amount } → PaymentConfirmResponse */
export function confirmPayment(body) {
  return apiClient.post('/payments/confirm', body);
}
