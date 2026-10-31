// 데모·확인용 실패 재현. 콘솔에서 window.__mockPayment.failNext(code)로 다음 요청 한 번을 실패시킨다.
// 토스 결제창에 다녀와도 남도록 sessionStorage에 둔다.

export const CONFIRM_FAILURES = [
  'PAYMENT_APPROVAL_FAILED',
  'PAYMENT_RESULT_UNKNOWN',
  'PAYMENT_CANCELED_AFTER_EXPIRY',
  'PAYMENT_EXPIRED',
];
// 정상 확인 때 보증금 반환 실패 → 200 + RETURNED(rental.md REQ-018)
export const DEPOSIT_REFUND_FAILED = 'DEPOSIT_REFUND_FAILED';

const STORAGE_KEY = 'mock.nextPaymentFailure';

export function failNext(code) {
  if (!CONFIRM_FAILURES.includes(code) && code !== DEPOSIT_REFUND_FAILED) {
    throw new Error(`모르는 실패 코드: ${code}`);
  }
  sessionStorage.setItem(STORAGE_KEY, code);
}

/** 걸어 둔 실패가 codes 중 하나면 꺼내서 돌려준다 */
export function takeFailure(codes) {
  const code = sessionStorage.getItem(STORAGE_KEY);
  if (!codes.includes(code)) {
    return null;
  }
  sessionStorage.removeItem(STORAGE_KEY);
  return code;
}
