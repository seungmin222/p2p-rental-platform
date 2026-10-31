// 토스페이먼츠 결제창(API 개별 연동, 결제위젯 아님). SDK는 결제할 때만 불러온다.

// 구매자가 결제창을 닫아 중단한 경우. PAY_PROCESS_CANCELED는 failUrl로도 온다.
const WINDOW_CLOSED_CODES = ['USER_CANCEL', 'PAY_PROCESS_CANCELED', 'PAYMENT_REQUEST_ABORTED'];

export function isWindowClosedCode(code) {
  return WINDOW_CLOSED_CODES.includes(code);
}

/** PaymentOrderResponse → requestPayment 인자. 복귀 주소는 오리진을 포함해야 한다. */
export function toTossPaymentRequest(order, origin) {
  return {
    method: 'CARD',
    amount: { currency: 'KRW', value: order.amount },
    orderId: order.orderId,
    orderName: order.orderName,
    successUrl: `${origin}/payments/success`,
    failUrl: `${origin}/payments/fail`,
  };
}

/** 결제창을 연다. 성공·실패는 successUrl·failUrl로 돌아오고, 창을 닫으면 거절될 수 있다. */
export async function openTossPaymentWindow(order) {
  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
  if (!clientKey) {
    throw Object.assign(new Error('결제 설정이 없어요. 잠시 후 다시 시도해 주세요'), {
      code: 'CLIENT_TOSS_KEY_MISSING',
    });
  }
  const { loadTossPayments } = await import('@tosspayments/tosspayments-sdk');
  const tossPayments = await loadTossPayments(clientKey);
  const payment = tossPayments.payment({ customerKey: order.customerKey });
  await payment.requestPayment(toTossPaymentRequest(order, window.location.origin));
}
