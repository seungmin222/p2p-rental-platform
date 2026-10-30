// 대여 목의 메모리 저장소와 기간 점유 판정. 대여 핸들러(rental.js)와 물품 검색 핸들러(feed.js)가 같이 쓴다.
// 새로고침하면 시드로 돌아간다.
import { createRentalSeed, toServerDateTime } from './rentalSeed';

export const db = createRentalSeed();

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
