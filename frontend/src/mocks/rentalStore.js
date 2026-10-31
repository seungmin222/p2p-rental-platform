// 대여·결제 목의 저장소와 기간 점유 판정. 대여 핸들러(rental.js)와 물품 검색 핸들러(feed.js)가 같이 쓴다.
// 토스 결제창에 다녀오면 페이지가 새로 뜨므로 sessionStorage에 둔다(탭을 닫으면 시드로 돌아간다).
// 처음부터 다시 하려면 콘솔에서 window.__mockPayment.reset().
import { placeholderImage } from './item';
import { createRentalSeed, toServerDateTime } from './rentalSeed';

const STORAGE_KEY = 'mock.rentalDb';

/** 올린 사진의 blob: 주소는 새로고침하면 죽으므로 자리표시 이미지로 바꾼다 */
function reviveImageUrls(urls) {
  return urls.map((url) => (url.startsWith('blob:') ? placeholderImage('올린 사진') : url));
}

function loadDb() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY));
    if (!saved) {
      return null;
    }
    for (const inspection of saved.inspections) {
      inspection.imageUrls = reviveImageUrls(inspection.imageUrls);
      inspection.resultImageUrls = reviveImageUrls(inspection.resultImageUrls);
    }
    return saved;
  } catch {
    return null;
  }
}

export const db = loadDb() ?? { ...createRentalSeed(), payments: [] };

export function saveDb() {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(db));
  } catch {
    // 저장하지 못하면 결제창에 다녀온 뒤 시드로 돌아갈 뿐이다
  }
}

/** 저장한 목 데이터를 지운다. 호출한 쪽이 새로고침해야 시드로 돌아간다 */
export function clearSavedDb() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // 지울 것이 없으면 그대로
  }
}

const ACTIVE_FOR_PERIOD = ['PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE'];

export function nowServer() {
  return toServerDateTime(new Date());
}

/** 결제 대기는 만료 전인 것만 기간을 점유한다(rental.md REQ-012) */
export function occupiesPeriod(rental) {
  if (!ACTIVE_FOR_PERIOD.includes(rental.status)) {
    return false;
  }
  return rental.status !== 'PENDING_PAYMENT' || rental.paymentExpiresAt > nowServer();
}
