// 결제 복귀 화면의 순수 함수. node 환경에서 테스트한다.

/** /payments/success 쿼리 → 승인 요청 본문. 값이 빠졌거나 금액이 양의 정수가 아니면 null */
export function parseSuccessParams(searchParams) {
  const paymentKey = searchParams.get('paymentKey');
  const orderId = searchParams.get('orderId');
  const amountText = searchParams.get('amount');
  if (!paymentKey || !orderId || !/^[1-9]\d*$/.test(amountText ?? '')) {
    return null;
  }
  const amount = Number(amountText);
  return Number.isSafeInteger(amount) ? { paymentKey, orderId, amount } : null;
}

const inFlight = new Map();

/**
 * 같은 key의 요청이 진행 중이면 그 Promise를 돌려준다(StrictMode의 effect 두 번 실행 등).
 * 끝난 뒤의 재호출(새로고침, 다시 확인)은 서버가 멱등하게 처리한다.
 */
export function runOnce(key, request) {
  if (!inFlight.has(key)) {
    inFlight.set(
      key,
      request().finally(() => inFlight.delete(key)),
    );
  }
  return inFlight.get(key);
}

const TO_DETAIL = 'DETAIL'; // 대여 상세로
const TO_HOME = 'HOME'; // 볼 대여가 없음
const RETRY = 'RETRY'; // 같은 요청을 다시 보낸다(서버가 중복 승인하지 않음)

const OUTCOMES = {
  PAYMENT_APPROVAL_FAILED: {
    title: '결제 승인에 실패했어요',
    message: '대여 상세에서 다시 결제할 수 있어요.',
    action: TO_DETAIL,
  },
  PAYMENT_RESULT_UNKNOWN: {
    title: '결제 결과를 확인하고 있어요',
    message: '잠시 후 다시 확인해 주세요. 두 번 결제되지 않아요.',
    action: RETRY,
  },
  PAYMENT_AMOUNT_MISMATCH: {
    title: '예약이 취소됐어요',
    message: '결제 금액이 일치하지 않아 예약이 취소됐어요.',
    action: TO_DETAIL,
  },
  PAYMENT_CANCELED_AFTER_EXPIRY: {
    title: '결제 금액을 환불해요',
    message: '결제 처리 중 예약이 만료되어 전액 환불을 진행해요.',
    action: TO_DETAIL,
  },
  PAYMENT_EXPIRED: {
    title: '결제 시간이 지났어요',
    message: '다시 예약해 주세요.',
    action: TO_DETAIL,
  },
  PAYMENT_INVALID_STATUS: {
    title: '이미 처리된 결제예요',
    message: '대여 상세에서 현재 상태를 확인해 주세요.',
    action: TO_DETAIL,
  },
  PAYMENT_RENTER_ONLY: {
    title: '결제할 수 없어요',
    message: '대여자만 결제할 수 있어요.',
    action: TO_HOME,
  },
  PAYMENT_NOT_FOUND: {
    title: '결제 내역을 찾을 수 없어요',
    message: '결제 정보가 올바르지 않아요.',
    action: TO_HOME,
  },
  COMMON_INVALID_INPUT: {
    title: '결제 정보가 올바르지 않아요',
    message: '결제를 다시 진행해 주세요.',
    action: TO_HOME,
  },
};

/** 승인 실패 → 화면 안내. 그 밖의 오류(CLIENT_* 등)는 결과를 알 수 없으므로 다시 확인하게 한다. */
export function getConfirmOutcome(error) {
  return (
    OUTCOMES[error?.code] ?? {
      title: '결제 결과를 확인하지 못했어요',
      message: error?.message ?? '잠시 후 다시 확인해 주세요.',
      action: RETRY,
    }
  );
}

export const CONFIRM_ACTION = { TO_DETAIL, TO_HOME, RETRY };
