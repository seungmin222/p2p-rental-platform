// 결제창에 다녀오는 동안 어느 대여의 어떤 결제인지 기억한다. 토스가 돌려주는 값에 rentalId가 없고, 구매자 취소는 orderId도 오지 않는다.
// kind는 결과 화면 문구를 고르는 데만 쓴다(RENTAL 대여료+보증금, EXTENSION 연장료 REQ-038).
const STORAGE_KEY = 'rental.pendingPayment';

export const PAYMENT_KIND = { RENTAL: 'RENTAL', EXTENSION: 'EXTENSION' };

export function savePendingPayment(rentalId, kind = PAYMENT_KIND.RENTAL, storage = sessionStorage) {
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify({ rentalId, kind }));
  } catch {
    // 저장하지 못해도 결제는 진행한다. 결과 화면은 빌린 내역으로 안내한다
  }
}

/** 기억한 rentalId. 없거나 값이 이상하면 null */
export function readPendingPayment(storage = sessionStorage) {
  try {
    const rentalId = JSON.parse(storage.getItem(STORAGE_KEY))?.rentalId;
    return Number.isSafeInteger(rentalId) && rentalId > 0 ? rentalId : null;
  } catch {
    return null;
  }
}

/** 기억한 결제 종류. 없거나 모르는 값이면 RENTAL(예전 저장값 포함) */
export function readPendingPaymentKind(storage = sessionStorage) {
  try {
    return JSON.parse(storage.getItem(STORAGE_KEY))?.kind === PAYMENT_KIND.EXTENSION
      ? PAYMENT_KIND.EXTENSION
      : PAYMENT_KIND.RENTAL;
  } catch {
    return PAYMENT_KIND.RENTAL;
  }
}

export function clearPendingPayment(storage = sessionStorage) {
  try {
    storage.removeItem(STORAGE_KEY);
  } catch {
    // 지울 것이 없으면 그대로
  }
}
