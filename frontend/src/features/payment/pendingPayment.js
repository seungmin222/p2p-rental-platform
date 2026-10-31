// 결제창에 다녀오는 동안 어느 대여인지 기억한다. 토스가 돌려주는 값에 rentalId가 없고, 구매자 취소는 orderId도 오지 않는다.
const STORAGE_KEY = 'rental.pendingPayment';

export function savePendingPayment(rentalId, storage = sessionStorage) {
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify({ rentalId }));
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

export function clearPendingPayment(storage = sessionStorage) {
  try {
    storage.removeItem(STORAGE_KEY);
  } catch {
    // 지울 것이 없으면 그대로
  }
}
