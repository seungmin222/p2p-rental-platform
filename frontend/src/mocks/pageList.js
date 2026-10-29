// 목록 목(빌린 내역·내 물품·물품별 예약 현황)의 순수 함수. 저장소(rental.js의 db)를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 탭 표를 여기 따로 둔다(state-machines.md "UI 탭 매핑").
import { compareDate } from '../shared/lib/date';

// policies.md rental-history.page (요청의 size는 받지 않는다). 내 물품·예약 현황도 같은 크기로 가정
export const PAGE_SIZE = 20;

/** 빌린 내역 탭 → 상태 */
export const HISTORY_TAB_STATUSES = {
  UPCOMING: ['PENDING_PAYMENT', 'RESERVED'],
  ONGOING: ['RENTING', 'OVERDUE', 'RETURNED'],
  DONE: ['COMPLETED', 'CANCELED'],
};

/** 내 물품 대여 중 탭 기준 */
const RENTING_STATUSES = ['RENTING', 'OVERDUE'];
/** 다가오는 예약(REQ-020): 이 상태 중 startDate ≥ 오늘 */
const UPCOMING_STATUSES = ['RESERVED', 'PENDING_PAYMENT'];

export const MY_ITEM_TABS = ['AVAILABLE', 'RENTING', 'HIDDEN'];

/** PageResponse 모양으로 자른다 */
export function toPageResponse(all, page, size = PAGE_SIZE) {
  const items = all.slice(page * size, (page + 1) * size);
  return {
    items,
    page,
    size,
    totalElements: all.length,
    hasNext: (page + 1) * size < all.length,
  };
}

/** 쿼리의 page. 없으면 0, 0 이상의 정수가 아니면 null(400) */
export function readPage(value) {
  if (value == null || value === '') {
    return 0;
  }
  return /^\d+$/.test(value) ? Number(value) : null;
}

/** 빌린 내역 정렬(rental.md REQ-019): OVERDUE 먼저 → startDate DESC → id DESC */
export function compareRentalHistory(a, b) {
  const overdue = Number(b.status === 'OVERDUE') - Number(a.status === 'OVERDUE');
  return overdue || compareDate(b.startDate, a.startDate) || b.rentalId - a.rentalId;
}

/** 내가 대여자인 건 중 tab에 속한 것을 정렬해서 */
export function listRentalHistory(rentals, renterId, tab) {
  const statuses = HISTORY_TAB_STATUSES[tab];
  return rentals
    .filter((rental) => rental.renterId === renterId && statuses.includes(rental.status))
    .sort(compareRentalHistory);
}

/** 물품에 RENTING·OVERDUE 대여가 있으면 대여 중 탭 */
export function isRentingItem(rentals, itemId) {
  return rentals.some(
    (rental) => rental.itemId === itemId && RENTING_STATUSES.includes(rental.status),
  );
}

/** 다가오는 예약 건수: RESERVED·PENDING_PAYMENT 중 startDate ≥ today */
export function countUpcomingRentals(rentals, itemId, today) {
  return rentals.filter(
    (rental) =>
      rental.itemId === itemId &&
      UPCOMING_STATUSES.includes(rental.status) &&
      compareDate(rental.startDate, today) >= 0,
  ).length;
}

/** 내 물품 중 tab에 속한 것(items는 삭제되지 않은 내 물품). 숨김(HIDDEN)은 1순위에 없어 빈 목록. 최근 등록(id 큰) 순 */
export function listMyItems(items, rentals, tab) {
  if (tab === 'HIDDEN') {
    return [];
  }
  const isRentingTab = tab === 'RENTING';
  return items
    .filter((item) => isRentingItem(rentals, item.itemId) === isRentingTab)
    .sort((a, b) => b.itemId - a.itemId);
}

/** 물품별 예약 현황(REQ-020 "기간순"): startDate ASC → id ASC */
export function listItemRentals(rentals, itemId) {
  return rentals
    .filter((rental) => rental.itemId === itemId)
    .sort((a, b) => compareDate(a.startDate, b.startDate) || a.rentalId - b.rentalId);
}
